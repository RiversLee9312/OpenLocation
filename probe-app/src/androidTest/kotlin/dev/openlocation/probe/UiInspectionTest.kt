package dev.openlocation.probe

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Development UI helper using the platform UiAutomation service, without idle-state heuristics. */
class UiInspectionTest {
    @Test
    fun inspect() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.containsKey("ui_action"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var candidate = instrumentation.uiAutomation.rootInActiveWindow
        repeat(15) {
            if (candidate == null) {
                android.os.SystemClock.sleep(100)
                candidate = instrumentation.uiAutomation.rootInActiveWindow
            }
        }
        val root = requireNotNull(candidate)
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 40 || nodes.size >= 2000) return
            nodes.add(node)
            repeat(node.childCount) { i -> node.getChild(i)?.let { walk(it, depth + 1) } }
        }
        walk(root, 0)
        val label = args.getString("ui_label")
        if (label != null) {
            val matches = nodes.filter {
                it.isVisibleToUser &&
                    (it.text?.toString() == label ||
                        it.contentDescription?.toString() == label ||
                        it.viewIdResourceName == label)
            }
            check(matches.size == 1) { "Expected unique label $label; matches=${matches.size}" }
            var current: AccessibilityNodeInfo? = matches.single()
            if (args.containsKey("ui_text")) {
                while (current != null && !current.isEditable) current = current.parent
                check(
                    current?.performAction(
                        AccessibilityNodeInfo.ACTION_SET_TEXT,
                        Bundle().apply {
                            putCharSequence(
                                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                args.getString("ui_text"),
                            )
                        },
                    ) == true
                ) {
                    "No editable ancestor"
                }
                return
            }
            while (current != null && !current.isClickable) current = current.parent
            check(current?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                "No clickable ancestor"
            }
        } else
            nodes.forEach { node ->
                val text =
                    node.text?.toString()
                        ?: node.contentDescription?.toString()
                        ?: if (node.isClickable && node.isVisibleToUser)
                            "[${node.viewIdResourceName}]"
                        else null
                if (!text.isNullOrBlank()) {
                    val rect = android.graphics.Rect()
                    node.getBoundsInScreen(rect)
                    instrumentation.sendStatus(
                        0,
                        Bundle().apply {
                            putString(
                                "stream",
                                "${text.replace('\n',' ')} | $rect | id=${node.viewIdResourceName} | checked=${node.isChecked}\n",
                            )
                        },
                    )
                }
            }
    }
}
