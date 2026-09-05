package com.flowpilot.app.widget

import android.widget.FrameLayout
import android.widget.RemoteViews
import com.flowpilot.app.R
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34])
class WidgetLintCompatibilityTest {
    @Test
    fun widgetInflatesThroughRemoteViewsOnSupportedAndroidVersions() {
        val context = RuntimeEnvironment.getApplication()
        val view = RemoteViews(context.packageName, R.layout.widget_flowpilot_control)
            .apply(context, FrameLayout(context))
        assertNotNull(view.findViewById<android.view.View>(R.id.widget_status_dot))
    }
}
