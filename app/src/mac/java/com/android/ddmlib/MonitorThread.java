/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ddmlib;

import com.android.ddmlib.jdwp.JdwpExtension;
import com.github.grishberg.android.adb.AdbLogger;
import com.github.grishberg.android.adb.NoOpLogger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.BufferOverflowException;
import java.nio.channels.*;
import java.util.*;

/**
 * Monitor open connections.
 */
final class MonitorThread extends Thread {

    private final DdmJdwpExtension mDdmJdwpExtension;

    private volatile boolean mQuit = false;

    // List of clients we're paying attention to
    // Used for locking so final.
    final private ArrayList<Client> mClientList;

    // The almighty mux
    private Selector mSelector;

    private final List<JdwpExtension> mJdwpExtensions;

    // port for "debug selected"
    private ServerSocketChannel mDebugSelectedChan;

    private int mNewDebugSelectedPort;

    private int mDebugSelectedPort = -1;

    /**
     * "Selected" client setup to answer debugging connection to the mNewDebugSelectedPort port.
     */
    private Client mSelectedClient = null;

    // singleton
    private static com.android.ddmlib.MonitorThread sInstance;
    public static AdbLogger log = new NoOpLogger();

    /**
     * Generic constructor.
     */
    private MonitorThread() {
        super("Monitor");
        mClientList = new ArrayList<Client>();

        mNewDebugSelectedPort = DdmPreferences.getSelectedDebugPort();

        mDdmJdwpExtension = new DdmJdwpExtension();
        mJdwpExtensions = new LinkedList<JdwpExtension>();
        mJdwpExtensions.add(mDdmJdwpExtension);
    }

    /**
     * Creates and return the singleton instance of the client monitor thread.
     */
    static com.android.ddmlib.MonitorThread createInstance() {
        return sInstance = new com.android.ddmlib.MonitorThread();
    }

    /**
     * Get singleton instance of the client monitor thread.
     */
    static com.android.ddmlib.MonitorThread getInstance() {
        return sInstance;
    }


    /**
     * Sets or changes the port number for "debug selected".
     */
    synchronized void setDebugSelectedPort(int port) throws IllegalStateException {
        if (sInstance == null) {
            return;
        }

        if (!AndroidDebugBridge.getClientSupport()) {
            return;
        }

        if (mDebugSelectedChan != null) {
            Log.d("ddms", "Changing debug-selected port to " + port);
            mNewDebugSelectedPort = port;
            wakeup();
        } else {
            // we set mNewDebugSelectedPort instead of mDebugSelectedPort so that it's automatically
            // opened on the first run loop.
            mNewDebugSelectedPort = port;
        }
    }

    /**
     * Sets the client to accept debugger connection on the custom "Selected debug port".
     *
     * @param selectedClient the client. Can be null.
     */
    synchronized void setSelectedClient(Client selectedClient) {
        if (sInstance == null) {
            return;
        }

        if (mSelectedClient != selectedClient) {
            Client oldClient = mSelectedClient;
            mSelectedClient = selectedClient;

            if (oldClient != null) {
                oldClient.update(Client.CHANGE_PORT);
            }

            if (mSelectedClient != null) {
                mSelectedClient.update(Client.CHANGE_PORT);
            }
        }
    }

    /**
     * Returns the client accepting debugger connection on the custom "Selected debug port".
     */
    Client getSelectedClient() {
        return mSelectedClient;
    }


    /**
     * Returns "true" if we want to retry connections to clients if we get a bad
     * JDWP handshake back, "false" if we want to just mark them as bad and
     * leave them alone.
     */
    boolean getRetryOnBadHandshake() {
        return true; // TODO? make configurable
    }

    /**
     * Get an array of known clients.
     */
    Client[] getClients() {
        synchronized (mClientList) {
            return mClientList.toArray(new Client[0]);
        }
    }

    /**
     * Register "handler" as the handler for type "type".
     */
    synchronized void registerChunkHandler(int type, ChunkHandler handler) {
        if (sInstance == null) {
            return;
        }
        mDdmJdwpExtension.registerHandler(type, handler);
    }

    /**
     * Watch for activity from clients and debuggers.
     */
    @Override
    public void run() {
        log.d("ddms", "Monitor is up");

        // create a selector
        try {
            mSelector = Selector.open();
        } catch (IOException ioe) {
            log.e("ddms", "Failed to initialize Monitor Thread: ", ioe);
            return;
        }

        while (!mQuit) {

            try {
                /*
                 * sync with new registrations: we wait until addClient is done before going through
                 * and doing mSelector.select() again.
                 * @see {@link #addClient(Client)}
                 */
                synchronized (mClientList) {
                }

                // (re-)open the "debug selected" port, if it's not opened yet or
                // if the port changed.
                try {
                    if (AndroidDebugBridge.getClientSupport()) {
                        if ((mDebugSelectedChan == null ||
                                mNewDebugSelectedPort != mDebugSelectedPort) &&
                                mNewDebugSelectedPort != -1) {
                            if (reopenDebugSelectedPort()) {
                                mDebugSelectedPort = mNewDebugSelectedPort;
                            }
                        }
                    }
                } catch (IOException ioe) {
                    log.e("ddms", "Failed to reopen debug port for Selected Client to: " + mNewDebugSelectedPort, ioe);
                    mNewDebugSelectedPort = mDebugSelectedPort; // no retry
                }

                int count;
                try {
                    count = mSelector.select();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                    continue;
                } catch (CancelledKeyException cke) {
                    continue;
                }

                if (count == 0) {
                    // somebody called wakeup() ?
                    // Log.i("ddms", "selector looping");
                    continue;
                }

                Set<SelectionKey> keys = mSelector.selectedKeys();
                Iterator<SelectionKey> iter = keys.iterator();

                while (iter.hasNext()) {
                    SelectionKey key = iter.next();
                    iter.remove();

                    try {
                        if (key.attachment() instanceof Client) {
                            processClientActivity(key);
                        } else if (key.attachment() instanceof Debugger) {
                            processDebuggerActivity(key);
                        } else if (key.attachment() instanceof com.android.ddmlib.MonitorThread) {
                            processDebugSelectedActivity(key);
                        } else {
                            log.e("ddms", "unknown activity key");
                        }
                    } catch (Exception e) {
                        // we don't want to have our thread be killed because of any uncaught
                        // exception, so we intercept all here.
                        log.e("ddms", "Exception during activity from Selector.", e);
                    }
                }
            } catch (Exception e) {
                // we don't want to have our thread be killed because of any uncaught
                // exception, so we intercept all here.
                log.e("ddms", "Exception MonitorThread.run()", e);
            }
        }
    }


    /**
     * Returns the port on which the selected client listen for debugger
     */
    int getDebugSelectedPort() {
        return mDebugSelectedPort;
    }

    /*
     * Something happened. Figure out what.
     */
    private void processClientActivity(SelectionKey key) {
        Client client = (Client) key.attachment();

        try {
            if (!key.isReadable() || !key.isValid()) {
                log.d("ddms", "Invalid key from " + client + ". Dropping client.");
                dropClient(client, true /* notify */);
                return;
            }

            client.read();

            /*
             * See if we have a full packet in the buffer. It's possible we have
             * more than one packet, so we have to loop.
             */
            JdwpPacket packet = client.getJdwpPacket();
            while (packet != null) {
                packet.log("Client: received jdwp packet");
                client.incoming(packet, client.getDebugger());

                packet.consume();
                // find next
                packet = client.getJdwpPacket();
            }
        } catch (CancelledKeyException e) {
            // key was canceled probably due to a disconnected client before we could
            // read stuff coming from the client, so we drop it.
            dropClient(client, true /* notify */);
        } catch (IOException ex) {
            // something closed down, no need to print anything. The client is simply dropped.
            dropClient(client, true /* notify */);
        } catch (Exception ex) {
            Log.e("ddms", ex);

            /* close the client; automatically un-registers from selector */
            dropClient(client, true /* notify */);

            if (ex instanceof BufferOverflowException) {
                log.w("ddms", "Client data packet exceeded maximum buffer size " + client);
            } else {
                // don't know what this is, display it
                Log.e("ddms", ex);
            }
        }
    }

    /**
     * Drops a client from the monitor.
     * <p>This will lock the {@link Client} list of the {@link Device} running <var>client</var>.
     *
     * @param client
     * @param notify
     */
    synchronized void dropClient(Client client, boolean notify) {
        if (sInstance == null) {
            return;
        }

        synchronized (mClientList) {
            if (!mClientList.remove(client)) {
                return;
            }
        }
        client.close(notify);
        mDdmJdwpExtension.broadcast(DdmJdwpExtension.Event.CLIENT_DISCONNECTED, client);

        /*
         * http://forum.java.sun.com/thread.jspa?threadID=726715&start=0
         * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5073504
         */
        wakeup();
    }

    /**
     * Drops the provided list of clients from the monitor. This will lock the {@link Client}
     * list of the {@link Device} running each of the clients.
     */
    synchronized void dropClients(Collection<? extends Client> clients, boolean notify) {
        for (Client c : clients) {
            dropClient(c, notify);
        }
    }

    /*
     * Process activity from one of the debugger sockets. This could be a new
     * connection or a data packet.
     */
    private void processDebuggerActivity(SelectionKey key) {
        Debugger dbg = (Debugger) key.attachment();

        try {
            if (key.isAcceptable()) {
                try {
                    acceptNewDebugger(dbg, null);
                } catch (IOException ioe) {
                    log.e("ddms", "debugger accept() failed", ioe);
                }
            } else if (key.isReadable()) {
                processDebuggerData(key);
            } else {
                log.d("ddm-debugger", "key in unknown state");
            }
        } catch (CancelledKeyException cke) {
            // key has been cancelled we can ignore that.
        }
    }

    /*
     * Accept a new connection from a debugger. If successful, register it with
     * the Selector.
     */
    private void acceptNewDebugger(Debugger dbg, ServerSocketChannel acceptChan)
            throws IOException {

        synchronized (mClientList) {
            SocketChannel chan;

            if (acceptChan == null)
                chan = dbg.accept();
            else
                chan = dbg.accept(acceptChan);

            if (chan != null) {
                chan.socket().setTcpNoDelay(true);

                wakeup();

                try {
                    chan.register(mSelector, SelectionKey.OP_READ, dbg);
                } catch (IOException ioe) {
                    // failed, drop the connection
                    dbg.closeData();
                    throw ioe;
                } catch (RuntimeException re) {
                    // failed, drop the connection
                    dbg.closeData();
                    throw re;
                }
            } else {
                log.w("ddms", "ignoring duplicate debugger");
                // new connection already closed
            }
        }
    }

    /*
     * We have incoming data from the debugger. Forward it to the client.
     */
    private void processDebuggerData(SelectionKey key) {
        Debugger dbg = (Debugger) key.attachment();

        dbg.processChannelData();
    }

    /*
     * Tell the thread that something has changed.
     */
    private void wakeup() {
        // If we didn't started running yet, we might not have a selector set.
        if (mSelector != null) {
            mSelector.wakeup();
        }
    }

    /**
     * Tell the thread to stop. Called from UI thread.
     */
    synchronized void quit() {
        mQuit = true;
        wakeup();
        log.d("ddms", "Waiting for Monitor thread");
        try {
            // since we're quitting, lets drop all the client and disconnect
            // the DebugSelectedPort
            synchronized (mClientList) {
                for (Client c : mClientList) {
                    int pid = -1;
                    String pkgName = "";
                    int listenPort = c.getDebugger() != null ? c.getDebugger().getListenPort() : -1;
                    if (c.getClientData() != null) {
                        pid = c.getClientData().getPid();
                        pkgName = c.getClientData().getPackageName();
                    }
                    log.d("ddms: close client:[pid=" + pid + ", pkg=" + pkgName + ", port=" + listenPort);
                    c.close(false /* notify */);
                    mDdmJdwpExtension.broadcast(DdmJdwpExtension.Event.CLIENT_DISCONNECTED, c);
                }
                mClientList.clear();
            }

            if (mDebugSelectedChan != null) {
                mDebugSelectedChan.close();
                mDebugSelectedChan.socket().close();
                mDebugSelectedChan = null;
            }
            if (mSelector != null) {
                mSelector.close();
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            log.e("ddms: quit error", e);
        }

        sInstance = null;
    }

    /**
     * Add a new Client to the list of things we monitor. Also adds the client's
     * channel and the client's debugger listener to the selection list. This
     * should only be called from one thread (the VMWatcherThread) to avoid a
     * race between "alreadyOpen" and Client creation.
     */
    synchronized void addClient(Client client) {
        if (sInstance == null) {
            return;
        }

        log.d("ddms: Adding new client pid=" + client.getClientData().getPid() + ", pkg=" + client.getClientData().getClientDescription());

        synchronized (mClientList) {
            mClientList.add(client);

            for (JdwpExtension extension : mJdwpExtensions) {
                extension.intercept(client);
            }

            /*
             * Register the Client's socket channel with the selector. We attach
             * the Client to the SelectionKey. If you try to register a new
             * channel with the Selector while it is waiting for I/O, you will
             * block. The solution is to call wakeup() and then hold a lock to
             * ensure that the registration happens before the Selector goes
             * back to sleep.
             */
            try {
                wakeup();

                client.register(mSelector);

                Debugger dbg = client.getDebugger();
                if (dbg != null) {
                    dbg.registerListener(mSelector);
                }
            } catch (IOException ioe) {
                // not really expecting this to happen
                ioe.printStackTrace();
            }
        }
    }

    /**
     * Opens (or reopens) the "debug selected" port and listen for connections.
     *
     * @return true if the port was opened successfully.
     * @throws IOException
     */
    private boolean reopenDebugSelectedPort() throws IOException {

        Log.d("ddms", "reopen debug-selected port: " + mNewDebugSelectedPort);
        if (mDebugSelectedChan != null) {
            mDebugSelectedChan.close();
        }

        mDebugSelectedChan = ServerSocketChannel.open();
        mDebugSelectedChan.configureBlocking(false); // required for Selector

        InetSocketAddress addr = new InetSocketAddress(
                InetAddress.getByName("localhost"), //$NON-NLS-1$
                mNewDebugSelectedPort);
        mDebugSelectedChan.socket().setReuseAddress(true); // enable SO_REUSEADDR

        try {
            mDebugSelectedChan.socket().bind(addr);
            if (mSelectedClient != null) {
                mSelectedClient.update(Client.CHANGE_PORT);
            }

            mDebugSelectedChan.register(mSelector, SelectionKey.OP_ACCEPT, this);

            return true;
        } catch (java.net.BindException e) {
            displayDebugSelectedBindError(mNewDebugSelectedPort);

            // do not attempt to reopen it.
            mDebugSelectedChan = null;
            mNewDebugSelectedPort = -1;

            return false;
        }
    }

    /*
     * We have some activity on the "debug selected" port. Handle it.
     */
    private void processDebugSelectedActivity(SelectionKey key) {
        assert key.isAcceptable();

        ServerSocketChannel acceptChan = (ServerSocketChannel) key.channel();

        /*
         * Find the debugger associated with the currently-selected client.
         */
        if (mSelectedClient != null) {
            Debugger dbg = mSelectedClient.getDebugger();

            if (dbg != null) {
                log.d("ddms", "Accepting connection on 'debug selected' port");
                try {
                    acceptNewDebugger(dbg, acceptChan);
                } catch (IOException ioe) {
                    // client should be gone, keep going
                }

                return;
            }
        }

        log.w("ddms", "Connection on 'debug selected' port, but none selected");
        try {
            SocketChannel chan = acceptChan.accept();
            chan.close();
        } catch (IOException ioe) {
            // not expected; client should be gone, keep going
        } catch (NotYetBoundException e) {
            displayDebugSelectedBindError(mDebugSelectedPort);
        }
    }

    private static void displayDebugSelectedBindError(int port) {
        String message =
                "Could not open Selected VM debug port ("
                        + port
                        + "). Make sure you do not have another instance of Android Studio or the Android plugin running. "
                        + "If it's being used by something else, choose a new port number in the preferences.";

        log.e("ddms", message);
    }

    public DdmJdwpExtension getDdmExtension() {
        return mDdmJdwpExtension;
    }
}
