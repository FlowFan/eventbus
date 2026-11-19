package com.github.flowfan.eventbus

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.psi.PsiElement
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getUParentForIdentifier
import javax.swing.Icon

/**
 * Links `EventBus.post(event)` / `postSticky(event)` calls and the `@Subscribe` methods receiving the event.
 * Markers are attached to leaf identifiers only, and targets are searched lazily when the gutter icon is clicked.
 */
class EventBusLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun getName(): String = EventBusBundle.message("lineMarker.name")

    override fun getIcon(): Icon = EventBusIcons.Post

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>,
    ) {
        if (element.firstChild != null) return

        when (val parent = getUParentForIdentifier(element)) {
            is UCallExpression -> postMarker(element, parent)
            is UMethod -> subscriberMarker(element, parent)
            else -> null
        }?.let(result::add)
    }

    private fun postMarker(identifier: PsiElement, call: UCallExpression): RelatedItemLineMarkerInfo<PsiElement>? {
        if (identifier.text !in EventBusNames.POST_METHODS) return null
        if (call.methodIdentifier?.sourcePsi != identifier) return null
        val eventName = call.postedEventType()?.presentableText ?: return null

        return NavigationGutterIconBuilder.create(EventBusIcons.Post)
            .setTargets(NotNullLazyValue.lazy {
                val eventType = (getUParentForIdentifier(identifier) as? UCallExpression)?.postedEventType()
                eventType?.let(EventBusSearch::findSubscribers).orEmpty()
            })
            .setTargetRenderer { EventBusTargetRenderer() }
            .setTooltipText(EventBusBundle.message("lineMarker.post.tooltip"))
            .setPopupTitle(EventBusBundle.message("lineMarker.post.popupTitle", eventName))
            .setEmptyPopupText(EventBusBundle.message("lineMarker.post.empty", eventName))
            .createLineMarkerInfo(identifier)
    }

    private fun subscriberMarker(identifier: PsiElement, method: UMethod): RelatedItemLineMarkerInfo<PsiElement>? {
        if (method.uastAnchor?.sourcePsi != identifier) return null
        val eventName = method.subscribedEventType()?.presentableText ?: return null

        return NavigationGutterIconBuilder.create(EventBusIcons.Subscriber)
            .setTargets(NotNullLazyValue.lazy {
                val eventType = (getUParentForIdentifier(identifier) as? UMethod)?.subscribedEventType()
                eventType?.let(EventBusSearch::findPostCalls).orEmpty()
            })
            .setTargetRenderer { EventBusTargetRenderer() }
            .setTooltipText(EventBusBundle.message("lineMarker.subscriber.tooltip"))
            .setPopupTitle(EventBusBundle.message("lineMarker.subscriber.popupTitle", eventName))
            .setEmptyPopupText(EventBusBundle.message("lineMarker.subscriber.empty", eventName))
            .createLineMarkerInfo(identifier)
    }
}