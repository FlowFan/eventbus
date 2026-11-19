package com.github.flowfan.eventbus

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object EventBusIcons {

    @JvmField
    val Post: Icon = IconLoader.getIcon("/icons/post.svg", EventBusIcons::class.java)

    @JvmField
    val Subscriber: Icon = IconLoader.getIcon("/icons/subscriber.svg", EventBusIcons::class.java)
}
