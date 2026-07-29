package app.arbor.chat.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.relocation.BringIntoViewModifierNode

/** Prevents child TextViews, selection handles, and expanding rich blocks from
 * issuing an implicit relocation which fights the chat's explicit follow policy.
 */
private class NoOpBringIntoViewNode : Modifier.Node(), BringIntoViewModifierNode {
    override suspend fun bringIntoView(
        childCoordinates: LayoutCoordinates,
        boundsProvider: () -> Rect?,
    ) = Unit
}

private class NoOpBringIntoViewElement : ModifierNodeElement<NoOpBringIntoViewNode>() {
    override fun create(): NoOpBringIntoViewNode = NoOpBringIntoViewNode()
    override fun update(node: NoOpBringIntoViewNode) = Unit
    override fun InspectorInfo.inspectableProperties() {
        name = "noOpBringIntoView"
    }
    override fun equals(other: Any?): Boolean = other is NoOpBringIntoViewElement
    override fun hashCode(): Int = javaClass.hashCode()
}

internal fun Modifier.noOpBringIntoView(): Modifier = this then NoOpBringIntoViewElement()
