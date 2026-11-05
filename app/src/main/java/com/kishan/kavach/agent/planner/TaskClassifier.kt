package com.kishan.kavach.agent.planner

import com.kishan.kavach.domain.tools.ToolGroup

/** Cheap keyword classifier: decides which tool groups Gemma gets to see (keeps the prompt small). */
object TaskClassifier {
    private val screen = Regex("""(?i)\b(tap|click|press|scroll|swipe|type|search|dhundo|khojo|open|kholo|play|chalao|screen|app|likho|dabao|select|install)\b|खोलो|चलाओ""")
    private val comms = Regex("""(?i)\b(message|msg|whatsapp|sms|contact|reply|text|bhejo|call|mail)\b|संदेश""")
    private val device = Regex("""(?i)\b(battery|storage|memory space|device|phone info|android version|wi-?fi|bluetooth|network|internet|installed apps|apps list)\b""")
    private val productivity = Regex("""(?i)\b(note|notes|reminder|remind|yaad|task|todo|to-do|calendar|event|meeting|schedule)\b|याद""")
    private val utility = Regex("""(?i)\b(time|date|clipboard|copy|url|link|settings|samay|tarikh|aaj)\b""")

    fun groups(text: String): Set<ToolGroup> {
        val g = linkedSetOf(ToolGroup.CORE)
        if (screen.containsMatchIn(text)) g += ToolGroup.SCREEN
        if (comms.containsMatchIn(text)) { g += ToolGroup.COMMS; g += ToolGroup.SCREEN }
        if (device.containsMatchIn(text)) g += ToolGroup.DEVICE
        if (productivity.containsMatchIn(text)) g += ToolGroup.PRODUCTIVITY
        g += ToolGroup.UTILITY
        return g
    }
}
