# Android Trace Viewer
Method Trace viewer for `.trace` files like [Android Profiler](https://developer.android.com/studio/profile/android-profiler) but works faster, has convenient control and  highlighting.

Also you can add custom bookmars and generate list of method-calls with showing bookmarks.

For now shows only `main` thread.

Latest version download from (releases) [https://github.com/Grigory-Rylov/android-trace-viewer/releases]

## Hotkeys
### Files
**Ctrl + O** - Open file dialog

**Ctrl + N** - Record new method trace file

### Navigation
**W** - Zoom in

**S** - Zoom out

**A** - Pan left

**D** - Pan right

**Left, Right, Up, Down** (Mouse Drag) -  Pan the view 

**C** - Center screen on selected element

**F** - Fit zoom to current found element (or selected if there is no found elements).

**Z** - Reset zoom to fit screen.

**Q** - Go to previous found element (bookmark)

**E** - Go to next found element (bookmark)

**Ctrl + C** - Copy name of selected element

### Find / Bookmarks
**Ctrl + F** - Focus to the Find element field, to find elements press Enter.

**Ctrl + M** - Add Bookmarks instead all found elements.

**M** - Add bookmark on selected element

**Esc** - Exit from search mode. (Hide all found elements)

**Ctrl + R** - Remove current bookmark

### Reporting
**Ctrl + P** - Generate duration report

For mac uses use **Command** instead **Ctrl**

## Bookmarks
Helps to mark some methods in trace as importan event.
Bookmarks are saved automatically after you close the Trace Viewer.
Boormarks are stored in *markers* folder.

## New Trace Recording
After clicking on *New Trace* icon or pressing *Ctrl + N* record new trace dialog will be opened.
Saved `.trace` files wiil be placed in `trace` folder

## Report generator
Generates flat list of methods with duration. Can be filtered by duration and/or is current method constructor.

## Highlighting: 
- ![#9ac7fa](https://placehold.it/20/9ac7fa?text=+) Choreographer.doFrame
- ![#96dbcc](https://placehold.it/20/96dbcc?text=+) measuring
- ![#c989ff](https://placehold.it/20/c989ff?text=+) layouting


