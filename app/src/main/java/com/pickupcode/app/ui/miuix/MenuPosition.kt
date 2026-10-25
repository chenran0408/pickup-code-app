package com.pickupcode.app.ui.miuix

import androidx.compose.ui.unit.*
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.PopupPositionProvider as MiuixPopupPositionProvider

/** 记录菜单优先向下，底部空间不足时向上；窄窗口和 RTL 都保留屏幕边距。 */
internal class MenuPosition(
    private val margin: Int,
    private val marginDp: androidx.compose.ui.unit.Dp = margin.dp,
) :
    PopupPositionProvider, MiuixPopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
        layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.right - popupContentSize.width else anchorBounds.left
        val below = anchorBounds.bottom + margin
        val y = if (below + popupContentSize.height <= windowSize.height - margin) below
            else anchorBounds.top - popupContentSize.height - margin
        fun fit(value: Int, window: Int, content: Int): Int {
            val edge = minOf(margin, ((window - content) / 2).coerceAtLeast(0))
            return value.coerceIn(edge, (window - content - edge).coerceAtLeast(edge))
        }
        return IntOffset(fit(x, windowSize.width, popupContentSize.width), fit(y, windowSize.height, popupContentSize.height))
    }

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowBounds: IntRect,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
        popupMargin: IntRect,
        alignment: MiuixPopupPositionProvider.Align,
    ): IntOffset {
        val localAnchor = IntRect(
            left = anchorBounds.left - windowBounds.left,
            top = anchorBounds.top - windowBounds.top,
            right = anchorBounds.right - windowBounds.left,
            bottom = anchorBounds.bottom - windowBounds.top,
        )
        val local = calculatePosition(
            localAnchor,
            IntSize(windowBounds.width, windowBounds.height),
            layoutDirection,
            popupContentSize,
        )
        return IntOffset(local.x + windowBounds.left, local.y + windowBounds.top)
    }

    override fun getMargins(): PaddingValues = PaddingValues(all = marginDp)
}
