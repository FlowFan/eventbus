package com.github.flowfan.eventbus

import com.intellij.codeInsight.navigation.impl.PsiTargetPresentationRenderer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import org.jetbrains.uast.*
import javax.swing.Icon

/**
 * Presents the identifiers returned by [EventBusSearch] as rich popup rows:
 * - subscriber: `onLogin(LoginEvent) [MAIN, sticky]`, container `LoginActivity`, location `LoginActivity.kt:42`
 * - post: `EventBus.getDefault().post(event)`, container `LoginRepository.login`, location `LoginRepository.java:87`
 */
internal class EventBusTargetRenderer : PsiTargetPresentationRenderer<PsiElement>() {

    override fun getElementText(element: PsiElement): String = when (val parent = getUParentForIdentifier(element)) {
        is UMethod -> subscriberText(parent)
        is UCallExpression -> postText(parent)
        else -> element.text
    }

    override fun getContainerText(element: PsiElement): String? {
        val parent = getUParentForIdentifier(element)
        val method = parent as? UMethod ?: parent?.getParentOfType<UMethod>()
        return listOfNotNull(
            method?.getContainingUClass()?.name,
            method?.name?.takeIf { parent is UCallExpression },
        ).joinToString(".").ifEmpty { null }
    }

    override fun getIcon(element: PsiElement): Icon? = when (val parent = getUParentForIdentifier(element)) {
        is UMethod -> parent.sourcePsi?.getIcon(0)
        else -> EventBusIcons.Post
    }

    override fun getPresentation(element: PsiElement): TargetPresentation =
        TargetPresentation.builder(getElementText(element))
            .icon(getIcon(element))
            .containerText(getContainerText(element))
            .locationText(location(element))
            .presentation()

    private fun subscriberText(method: UMethod): String {
        val parameterType = method.uastParameters.singleOrNull()?.type?.presentableText.orEmpty()
        val annotation = method.findAnnotation(EventBusNames.SUBSCRIBE)
        val options = listOfNotNull(
            annotation?.findDeclaredAttributeValue("threadMode")?.sourcePsi?.text?.substringAfterLast('.'),
            "sticky".takeIf { annotation?.findDeclaredAttributeValue("sticky")?.evaluate() == true },
        )
        val suffix = if (options.isEmpty()) "" else options.joinToString(", ", prefix = " [", postfix = "]")
        return "${method.name}($parameterType)$suffix"
    }

    private fun postText(call: UCallExpression): String {
        val expression: UElement =
            (call.uastParent as? UQualifiedReferenceExpression)?.takeIf { it.selector == call } ?: call
        val text = expression.sourcePsi?.text?.replace(Regex("\\s+"), " ") ?: call.methodName.orEmpty()
        return StringUtil.shortenTextWithEllipsis(text, 100, 0)
    }

    private fun location(element: PsiElement): String? {
        val file = element.containingFile ?: return null
        val line = PsiDocumentManager.getInstance(element.project).getDocument(file)?.getLineNumber(element.textOffset)
        return if (line == null) file.name else "${file.name}:${line + 1}"
    }
}
