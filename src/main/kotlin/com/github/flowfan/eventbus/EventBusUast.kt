package com.github.flowfan.eventbus

import com.intellij.psi.PsiClassType
import com.intellij.psi.util.TypeConversionUtil
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod

internal object EventBusNames {
    const val EVENT_BUS = "org.greenrobot.eventbus.EventBus"
    const val SUBSCRIBE = "org.greenrobot.eventbus.Subscribe"
    val POST_METHODS = setOf("post", "postSticky")
}

/** Event type passed to `EventBus.post(event)` / `EventBus.postSticky(event)`, or `null` if this is not such a call. */
internal fun UCallExpression.postedEventType(): PsiClassType? {
    if (methodName !in EventBusNames.POST_METHODS || valueArgumentCount != 1) return null
    val method = resolve() ?: return null
    if (method.containingClass?.qualifiedName != EventBusNames.EVENT_BUS) return null
    return argumentEventType()
}

/** Event type of a call already known to be `EventBus.post` / `postSticky`, without resolving it again. */
internal fun UCallExpression.argumentEventType(): PsiClassType? =
    valueArguments.singleOrNull()?.getExpressionType()?.erasedClassType()

/** Event type received by an `@Subscribe` method, or `null` if this is not a subscriber method. */
internal fun UMethod.subscribedEventType(): PsiClassType? {
    val parameter = uastParameters.singleOrNull() ?: return null
    if (findAnnotation(EventBusNames.SUBSCRIBE) == null) return null
    return parameter.type.erasedClassType()
}

/** EventBus dispatches by runtime class and, by default, to subscribers of every supertype. */
internal fun PsiClassType.receives(eventType: PsiClassType): Boolean = isAssignableFrom(eventType)

private fun com.intellij.psi.PsiType.erasedClassType(): PsiClassType? =
    TypeConversionUtil.erasure(this) as? PsiClassType
