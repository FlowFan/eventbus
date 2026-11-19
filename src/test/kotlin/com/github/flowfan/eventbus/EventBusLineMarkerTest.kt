package com.github.flowfan.eventbus

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getUastParentOfType

class EventBusLineMarkerTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject(
            "org/greenrobot/eventbus/EventBus.java",
            """
            package org.greenrobot.eventbus;
            public class EventBus {
                public static EventBus getDefault() { return null; }
                public void post(Object event) {}
                public void postSticky(Object event) {}
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "org/greenrobot/eventbus/Subscribe.java",
            """
            package org.greenrobot.eventbus;
            @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
            @java.lang.annotation.Target(java.lang.annotation.ElementType.METHOD)
            public @interface Subscribe {
                ThreadMode threadMode() default ThreadMode.POSTING;
                boolean sticky() default false;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "org/greenrobot/eventbus/ThreadMode.java",
            "package org.greenrobot.eventbus; public enum ThreadMode { POSTING, MAIN }"
        )
        myFixture.addFileToProject("demo/BaseEvent.java", "package demo; public class BaseEvent {}")
        myFixture.addFileToProject("demo/LoginEvent.java", "package demo; public class LoginEvent extends BaseEvent {}")
        myFixture.addFileToProject("demo/LogoutEvent.java", "package demo; public class LogoutEvent {}")
        myFixture.addFileToProject(
            "demo/QueryRelationShipSuccessEvent.java",
            "package demo; public class QueryRelationShipSuccessEvent { public java.util.List<String> targetUsers; }"
        )
        myFixture.addFileToProject(
            "demo/JavaReceiver.java",
            """
            package demo;
            import org.greenrobot.eventbus.Subscribe;
            public class JavaReceiver {
                @Subscribe public void onLoginJava(LoginEvent event) {}
                @Subscribe public void onBaseJava(BaseEvent event) {}
                @Subscribe public void onLogoutJava(LogoutEvent event) {}
                public void notSubscriber(LoginEvent event) {}
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "demo/KotlinReceiver.kt",
            """
            package demo
            import org.greenrobot.eventbus.Subscribe
            class KotlinReceiver {
                @Subscribe fun onLoginKotlin(event: LoginEvent) {}
                @Subscribe fun onLogoutKotlin(event: LogoutEvent) {}
            }
            """.trimIndent()
        )
    }

    fun testJavaPostNavigatesToSubscribersIncludingSupertypes() {
        myFixture.configureByText(
            "JavaSender.java",
            """
            package demo;
            import org.greenrobot.eventbus.EventBus;
            class JavaSender {
                void sendLogin() { EventBus.getDefault().po<caret>st(new LoginEvent()); }
            }
            """.trimIndent()
        )

        assertSameElements(targetsAtCaret(), "onLoginJava", "onBaseJava", "onLoginKotlin")
    }

    fun testKotlinPostStickyNavigatesToSubscribers() {
        myFixture.configureByText(
            "KotlinSender.kt",
            """
            package demo
            import org.greenrobot.eventbus.EventBus
            class KotlinSender {
                fun sendLogout() { EventBus.getDefault().postSti<caret>cky(LogoutEvent()) }
            }
            """.trimIndent()
        )

        assertSameElements(targetsAtCaret(), "onLogoutJava", "onLogoutKotlin")
    }

    fun testJavaSubscriberNavigatesToPostsOfSubtypes() {
        addSenders()
        myFixture.configureFromTempProjectFile("demo/JavaReceiver.java")
        moveCaretTo("onBaseJava")

        assertSameElements(targetsAtCaret(), "sendLoginJava", "sendLoginKotlin")
    }

    fun testKotlinSubscriberNavigatesToPosts() {
        addSenders()
        myFixture.configureFromTempProjectFile("demo/KotlinReceiver.kt")
        moveCaretTo("onLogoutKotlin")

        assertSameElements(targetsAtCaret(), "sendLogoutKotlin")
    }

    fun testEventPostedThroughLocalVariableNavigatesBothWays() {
        myFixture.addFileToProject(
            "demo/RelationReceiver.java",
            """
            package demo;
            import org.greenrobot.eventbus.Subscribe;
            public class RelationReceiver {
                @Subscribe public void onQueryRelationShipSuccess(QueryRelationShipSuccessEvent event) {}
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "demo/RelationSender.java",
            """
            package demo;
            import org.greenrobot.eventbus.EventBus;
            class RelationSender {
                void queryRelationShip(java.util.List<String> targetUsers) {
                    QueryRelationShipSuccessEvent event = new QueryRelationShipSuccessEvent();
                    event.targetUsers = targetUsers;
                    EventBus.getDefault().post(event);
                }
            }
            """.trimIndent()
        )

        val renderer = EventBusTargetRenderer()

        myFixture.configureFromTempProjectFile("demo/RelationSender.java")
        moveCaretTo("post(event)")
        val subscriber = renderer.getPresentation(targetElementsAtCaret().single())
        assertEquals("onQueryRelationShipSuccess(QueryRelationShipSuccessEvent)", subscriber.presentableText)
        assertEquals("RelationReceiver", subscriber.containerText)
        assertEquals("RelationReceiver.java:4", subscriber.locationText)

        myFixture.configureFromTempProjectFile("demo/RelationReceiver.java")
        moveCaretTo("onQueryRelationShipSuccess")
        val post = renderer.getPresentation(targetElementsAtCaret().single())
        assertEquals("EventBus.getDefault().post(event)", post.presentableText)
        assertEquals("RelationSender.queryRelationShip", post.containerText)
        assertEquals("RelationSender.java:7", post.locationText)
    }

    fun testSubscriberRowShowsThreadModeAndSticky() {
        val file = myFixture.addFileToProject(
            "demo/StickyReceiver.kt",
            """
            package demo
            import org.greenrobot.eventbus.Subscribe
            import org.greenrobot.eventbus.ThreadMode
            class StickyReceiver {
                @Subscribe(threadMode = ThreadMode.MAIN, sticky = true) fun onLogout(event: LogoutEvent) {}
            }
            """.trimIndent()
        )
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        moveCaretTo("onLogout")
        val identifier = myFixture.file.findElementAt(myFixture.caretOffset)!!

        assertEquals("onLogout(LogoutEvent) [MAIN, sticky]", EventBusTargetRenderer().getElementText(identifier))
    }

    fun testNoMarkersOutsideEventBus() {
        myFixture.configureByText(
            "Other.java",
            """
            package demo;
            class Other {
                void post(Object event) {}
                void send() { po<caret>st(new LoginEvent()); }
            }
            """.trimIndent()
        )

        assertEmpty(myFixture.findGuttersAtCaret())
    }

    private fun addSenders() {
        myFixture.addFileToProject(
            "demo/JavaSender.java",
            """
            package demo;
            import org.greenrobot.eventbus.EventBus;
            class JavaSender {
                void sendLoginJava() { EventBus.getDefault().post(new LoginEvent()); }
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "demo/KotlinSender.kt",
            """
            package demo
            import org.greenrobot.eventbus.EventBus
            class KotlinSender {
                fun sendLoginKotlin() = EventBus.getDefault().post(LoginEvent())
                fun sendLogoutKotlin() = EventBus.getDefault().postSticky(LogoutEvent())
            }
            """.trimIndent()
        )
    }

    private fun moveCaretTo(text: String) {
        myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.text.indexOf(text) + 1)
    }

    /** Names of the methods containing the navigation targets of the EventBus gutter icon at the caret. */
    private fun targetsAtCaret(): List<String> =
        targetElementsAtCaret().map { it.getUastParentOfType<UMethod>()?.name ?: it.text }

    private fun targetElementsAtCaret(): List<PsiElement> {
        val markerInfo = myFixture.findGuttersAtCaret()
            .filterIsInstance<LineMarkerInfo.LineMarkerGutterIconRenderer<*>>()
            .map { it.lineMarkerInfo }
            .filterIsInstance<RelatedItemLineMarkerInfo<*>>()
            .single()
        return markerInfo.createGotoRelatedItems().mapNotNull { it.element }
    }
}
