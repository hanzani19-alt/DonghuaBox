package io.nekohasekai.sagernet.widget

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.PointerIcon
import android.view.View
import androidx.appcompat.widget.TooltipCompat
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.BaseProgressIndicator
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.bg.BaseService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

class ServiceButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FloatingActionButton(context, attrs, defStyleAttr),
    DynamicAnimation.OnAnimationEndListener {

    private var checked = false
    private var delayedAnimation: Job? = null
    private lateinit var progress: BaseProgressIndicator<*>

    fun initProgress(progress: BaseProgressIndicator<*>) {
        this.progress = progress
        progress.progressDrawable?.addSpringAnimationEndListener(this)
    }

    override fun onAnimationEnd(
        animation: DynamicAnimation<out DynamicAnimation<*>>?,
        canceled: Boolean,
        value: Float,
        velocity: Float
    ) {
        if (!canceled) progress.hide()
    }

    private fun hideProgress() {
        delayedAnimation?.cancel()
        progress.hide()
    }

    override fun onCreateDrawableState(extraSpace: Int): IntArray {
        val drawableState = super.onCreateDrawableState(extraSpace + 1)
        if (checked) {
            View.mergeDrawableStates(
                drawableState,
                intArrayOf(android.R.attr.state_checked)
            )
        }
        return drawableState
    }

    fun changeState(
        state: BaseService.State,
        previousState: BaseService.State,
        animate: Boolean
    ) {
        when (state) {
            BaseService.State.Connecting -> {
                setImageResource(R.drawable.ic_medusa_raised)
                hideProgress()
                delayedAnimation =
                    (context as LifecycleOwner).lifecycleScope.launchWhenStarted {
                        delay(
                            context.resources.getInteger(
                                android.R.integer.config_mediumAnimTime
                            ) + 1000L
                        )
                        isIndeterminate = true
                        show()
                    }
            }

            BaseService.State.Connected -> {
                setImageResource(R.drawable.ic_medusa_cobra)
                delayedAnimation?.cancel()
                setProgressCompat(1, true)
            }

            BaseService.State.Stopping -> {
                setImageResource(R.drawable.ic_medusa_raised)
                hideProgress()
            }

            else -> {
                setImageResource(R.drawable.ic_medusa_coiled)
                hideProgress()
            }
        }

        checked = state == BaseService.State.Connected
        refreshDrawableState()

        val description = context.getText(
            if (state.canStop) R.string.stop else R.string.connect
        )
        contentDescription = description
        TooltipCompat.setTooltipText(this, description)

        val enabled = state.canStop || state == BaseService.State.Stopped
        isEnabled = enabled

        if (Build.VERSION.SDK_INT >= 24) {
            pointerIcon = PointerIcon.getSystemIcon(
                context,
                if (enabled) PointerIcon.TYPE_HAND else PointerIcon.TYPE_WAIT
            )
        }
    }
}
