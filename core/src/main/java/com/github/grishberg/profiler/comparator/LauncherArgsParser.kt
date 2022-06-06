package com.github.grishberg.profiler.comparator

import java.io.File

data class AggregateParseArgsResult(val reference: List<File>, val tested: List<File>)

data class CompareTracesParseArgsResult(val reference: String?, val tested: String?)

class LauncherArgsParser {
    fun parseAggregateArgs(args: Array<String>): AggregateParseArgsResult? {
        check(args.size >= 5 && args.first() == "--agg" && args[1] == "-ref")

        val reference = mutableListOf<File>()
        val tested = mutableListOf<File>()
        var index = 2
        while (args[index] != "-tested") {
            reference.add(File(args[index]))
            index++
            if (index >= args.size) {
                println("Aggregate should contain -ref and -tested params")
                return null
            }
        }
        index++
        while (index < args.size) {
            tested.add(File(args[index]))
            index++
        }

        return AggregateParseArgsResult(reference, tested)
    }

    fun parseCompareTracesArgs(args: Array<String>): CompareTracesParseArgsResult {
        check(args.isNotEmpty() && args.first() == "--cmp")

        val reference = args.getOrNull(1)
        val tested = args.getOrNull(2)

        return CompareTracesParseArgsResult(reference, tested)
    }
}
