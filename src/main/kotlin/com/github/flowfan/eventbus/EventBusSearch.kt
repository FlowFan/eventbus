package com.github.flowfan.eventbus

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.CommonClassNames
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getUastParentOfType
import org.jetbrains.uast.toUElementOfType

/**
 * Searches are first narrowed with the word index to the files mentioning the event class (or a related class),
 * and results are cached until the next PSI change, so clicking a gutter icon stays fast in large projects.
 */
internal object EventBusSearch {

    /** Name identifiers of the `@Subscribe` methods that receive [eventType], sorted by location. */
    fun findSubscribers(eventType: PsiClassType): List<PsiElement> {
        val eventClass = eventType.resolve() ?: return emptyList()
        return CachedValuesManager.getProjectPsiDependentCache(eventClass, ::computeSubscribers)
    }

    /** Method name identifiers of the `post` / `postSticky` calls whose event is received by [subscribedType], sorted by location. */
    fun findPostCalls(subscribedType: PsiClassType): List<PsiElement> {
        val subscribedClass = subscribedType.resolve() ?: return emptyList()
        return CachedValuesManager.getProjectPsiDependentCache(subscribedClass, ::computePostCalls)
    }

    private fun computeSubscribers(eventClass: PsiClass): List<PsiElement> {
        val project = eventClass.project
        val eventType = JavaPsiFacade.getElementFactory(project).createType(eventClass)
        val subscribe = JavaPsiFacade.getInstance(project)
            .findClass(EventBusNames.SUBSCRIBE, GlobalSearchScope.allScope(project))
            ?: return emptyList()

        // A subscriber declares the event class or one of its supertypes as its parameter type.
        val receivableClasses = LinkedHashSet<PsiClass>().apply {
            add(eventClass)
            InheritanceUtil.getSuperClasses(eventClass, this, true)
        }
        val scope = filesMentioning(project, receivableClasses)

        return AnnotatedElementsSearch.searchPsiMethods(subscribe, scope)
            .findAll()
            .mapNotNull { it.toUElementOfType<UMethod>() }
            .filter { it.subscribedEventType()?.receives(eventType) == true }
            .mapNotNull { it.uastAnchor?.sourcePsi }
            .sortedByLocation()
    }

    private fun computePostCalls(subscribedClass: PsiClass): List<PsiElement> {
        val project = subscribedClass.project
        val subscribedType = JavaPsiFacade.getElementFactory(project).createType(subscribedClass)
        val eventBus = JavaPsiFacade.getInstance(project)
            .findClass(EventBusNames.EVENT_BUS, GlobalSearchScope.allScope(project))
            ?: return emptyList()

        // A posting file mentions the subscribed class or one of its subclasses, e.g. in `new LoginEvent()`.
        val scope = if (subscribedClass.qualifiedName == CommonClassNames.JAVA_LANG_OBJECT) {
            GlobalSearchScope.projectScope(project)
        } else {
            val postableClasses = LinkedHashSet<PsiClass>().apply {
                add(subscribedClass)
                addAll(ClassInheritorsSearch.search(subscribedClass, GlobalSearchScope.projectScope(project), true).findAll())
            }
            filesMentioning(project, postableClasses)
        }

        return EventBusNames.POST_METHODS
            .flatMap { eventBus.findMethodsByName(it, false).asList() }
            .flatMap { MethodReferencesSearch.search(it, scope, true).findAll() }
            .mapNotNull { it.element.getUastParentOfType<UCallExpression>() }
            .filter { call -> call.argumentEventType()?.let(subscribedType::receives) == true }
            .mapNotNull { it.methodIdentifier?.sourcePsi }
            .sortedByLocation()
    }

    /** Project files whose code contains the short name of any of [classes]; an index lookup, no parsing involved. */
    private fun filesMentioning(project: Project, classes: Collection<PsiClass>): GlobalSearchScope {
        val projectScope = GlobalSearchScope.projectScope(project)
        val helper = PsiSearchHelper.getInstance(project)
        val files = HashSet<VirtualFile>()
        classes
            .filter { it.qualifiedName != CommonClassNames.JAVA_LANG_OBJECT }
            .mapNotNullTo(HashSet()) { it.name }
            .forEach { name ->
                helper.processCandidateFilesForText(projectScope, UsageSearchContext.IN_CODE, true, name) { files.add(it) }
            }
        return GlobalSearchScope.filesScope(project, files)
    }

    private fun List<PsiElement>.sortedByLocation(): List<PsiElement> =
        distinct().sortedWith(compareBy({ it.containingFile?.virtualFile?.path }, { it.textOffset }))
}
