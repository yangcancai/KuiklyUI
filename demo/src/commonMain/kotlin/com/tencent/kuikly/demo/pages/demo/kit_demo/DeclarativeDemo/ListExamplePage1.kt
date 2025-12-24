package com.tencent.kuikly.demo.pages.demo.kit_demo.DeclarativeDemo

import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.timer.setTimeout
import com.tencent.kuikly.core.views.*
import com.tencent.kuikly.core.views.compose.Button
import com.tencent.kuikly.demo.pages.base.BasePager
import com.tencent.kuikly.demo.pages.demo.base.NavBar
import kotlin.math.max
import kotlin.random.Random

/* -------------------- 数据模型 -------------------- */

internal class ListExampleModel1(
    val index: Int,
    val title: String,
    val color: Color,
)

/* -------------------- Page -------------------- */

@Page("ListExamplePage1")
internal class ListExamplePage1 : BasePager() {

    companion object {
        private const val TOTAL_COUNT = 1_000_000
        private const val WINDOW_SIZE = 200
        private const val ITEM_HEIGHT = 80f
    }

    private val randomHelper = Random.Default

    private lateinit var scrollView: ListView<*, *>

    private var windowStartIndex by observable(0)
    private var listedModels by observableList<ListExampleModel1>()
    private var lastAnchorIndex = -1
    private var isAdjustingWindow = false

    private var anchorIndex by observable(0)
    private var anchorOffsetInItem by observable(0f)

    override fun pageDidAppear() {
        loadWindow(0)
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr { backgroundColor(Color.WHITE) }

            NavBar {
                attr { title = "Virtual List Example" }
            }

            /* ----------- 操作按钮 ----------- */

            View {
                attr {
                    height(56f)
                    flexDirectionRow()
                    justifyContentSpaceAround()
                    alignItemsCenter()
                }

                Button {
                    attr {
                        titleAttr { text("跳到 0") }
                    }
                    event { click { ctx.jumpTo(0) } }
                }

                Button {
                    attr {
                        titleAttr { text("跳到 500000") }
                    }
                    event { click { ctx.jumpTo(500_000) } }
                }

                Button {
                    attr {
                        titleAttr { text("跳到 999999") }
                    }
                    event { click { ctx.jumpTo(999_999) } }
                }
            }

            /* ----------- List ----------- */

            List {
                ctx.scrollView = this

                attr {
                    flex(1f)
                }

                event {
                    scrollEnd{ scrollParams ->
                        if (ctx.isAdjustingWindow) return@scrollEnd
                        val raw = scrollParams.offsetY / ITEM_HEIGHT
                        val deltaIndex = raw.toInt()
                        val deltaOffset = scrollParams.offsetY % ITEM_HEIGHT

                        println("offsetY: ${scrollParams.offsetY}, deltaIndex: $deltaIndex, deltaOffset: $deltaOffset")
                        ctx.anchorIndex = ctx.windowStartIndex + deltaIndex
                        ctx.anchorOffsetInItem = deltaOffset
                        println("anchorIndex: ${ctx.anchorIndex}, anchorOffsetInItem: ${ctx.anchorOffsetInItem}")
                        ctx.maybeShiftWindow()
//                        ctx.handleScroll(scrollParams.offsetY)
                    }
                }

                vfor({ ctx.listedModels }) { model ->
                    View {
                        attr {
                            height(ITEM_HEIGHT)
                            backgroundColor(model.color)
                            allCenter()
                        }
                        Text {
                            attr {
                                fontSize(16f)
                                text("Index: ${model.index}")
                            }
                        }
                    }
                }
            }
        }
    }

    /* -------------------- 核心逻辑 -------------------- */

    /**
     * 加载一个窗口的数据
     */
    private fun loadWindow(startIndex: Int) {
        val safeStart = startIndex.coerceIn(
            0,
            TOTAL_COUNT - WINDOW_SIZE
        )

        windowStartIndex = safeStart
        listedModels.clear()

        val models = (0 until WINDOW_SIZE).map { offset ->
            val realIndex = safeStart + offset
            ListExampleModel1(
                index = realIndex,
                title = "Item #$realIndex",
                color = randomColor(realIndex)
            )
        }
        listedModels.addAll(models)
    }
    private fun maybeShiftWindow() {
        val lowerBound = windowStartIndex + WINDOW_SIZE / 4
        val upperBound = windowStartIndex + WINDOW_SIZE * 3 / 4

        if (anchorIndex in lowerBound..upperBound) return

        shiftWindow()
    }
    private fun shiftWindow() {
        val newWindowStart =
            (anchorIndex - WINDOW_SIZE / 2)
                .coerceIn(0, TOTAL_COUNT - WINDOW_SIZE)

        if (newWindowStart == windowStartIndex) return

        println("shiftWindow: newWindowStart = $newWindowStart")
        isAdjustingWindow = true

        loadWindow(newWindowStart)

        setTimeout(16) {
            val newOffset =
                (anchorIndex - windowStartIndex) * ITEM_HEIGHT +
                        anchorOffsetInItem

            println("setContentOffset to $newOffset")
            scrollView.setContentOffset(
                0f,
                newOffset,
                false
            )

            isAdjustingWindow = false
        }
    }

    /**
     * 处理滚动，动态替换窗口
     */
    private fun handleScroll(offsetY: Float) {
        if (isAdjustingWindow) return

        val visibleOffset = offsetY / ITEM_HEIGHT
        val anchorIndex = windowStartIndex + visibleOffset.toInt()

        // 防止同一个 anchor 反复触发
        if (anchorIndex == lastAnchorIndex) return
        lastAnchorIndex = anchorIndex

        // -------- 向下滚动，接近窗口底部 --------
        if (anchorIndex >= windowStartIndex + WINDOW_SIZE * 3 / 4) {
            shiftWindow(anchorIndex)
        }

        // -------- 向上滚动，接近窗口顶部 --------
        if (anchorIndex <= windowStartIndex + WINDOW_SIZE / 4) {
            shiftWindow(anchorIndex)
        }
    }
    private fun shiftWindow(anchorIndex: Int) {
        val newWindowStart =
            (anchorIndex - WINDOW_SIZE / 2)
                .coerceIn(0, TOTAL_COUNT - WINDOW_SIZE)

        // 没有实际变化，不处理
        if (newWindowStart == windowStartIndex) return

        isAdjustingWindow = true

        val anchorOffsetInWindow =
            (anchorIndex - newWindowStart) * ITEM_HEIGHT

        loadWindow(newWindowStart)

        // 保证 anchor 行仍然在原来的视觉位置
        setTimeout(0) {
            scrollView.setContentOffset(
                0f,
                anchorOffsetInWindow,
                false
            )
            isAdjustingWindow = false
        }
    }


    /**
     * 跳转到任意行
     */
    private fun jumpTo(index: Int) {
        val target = index.coerceIn(0, TOTAL_COUNT - 1)
        val newWindowStart = target - WINDOW_SIZE / 2

        loadWindow(newWindowStart)

        setTimeout(16) {
            val offsetInWindow =
                (target - windowStartIndex) * ITEM_HEIGHT
            scrollView.setContentOffset(
                0f,
                offsetInWindow,
                false
            )
        }
    }

    /* -------------------- 工具方法 -------------------- */

    private fun randomColor(index: Int): Color {
        val r = (index * 37) % 255
        val g = (index * 73) % 255
        val b = (index * 19) % 255
        return Color(0xFF000000L or
                (r.toLong() shl 16) or
                (g.toLong() shl 8) or
                b.toLong()
        )
    }
}
