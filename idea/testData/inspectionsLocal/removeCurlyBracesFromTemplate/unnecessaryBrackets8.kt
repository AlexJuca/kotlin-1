// HIGHLIGHT: GENERIC_ERROR_OR_WARNING

fun foo() {
    val x = 4
    val y = "text $<caret>{x} moretext"
}