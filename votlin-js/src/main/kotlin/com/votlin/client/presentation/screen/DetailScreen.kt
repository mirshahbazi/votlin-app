package com.votlin.client.presentation.screen

import com.votlin.client.presentation.*
import com.votlin.model.Talk
import react.RBuilder
import react.RProps
import react.dom.*
import react.setState

class DetailScreen : RootScreen<DetailProps, DetailState, DetailView>(), DetailView {
    override val presenter: Presenter<DetailView> = DetailPresenter(
            repository = repository,
            executor = executor,
            errorHandler = errorHandler,
            view = this
    )

    override fun RBuilder.render() {
        div("detail") {
            if (state.talk != undefined) {
                div("toolbar") {
                    img { attrs.src = "https://www.materialui.co/materialIcons/navigation/arrow_back_white_192x192.png" }
                    span { +state.talk.name }
                }

                div(classes = "card ${state.talk.track.toString().toLowerCase()}") {
                    h3 { +state.talk.track.toString().toLowerCase().capitalize() }
                }
                span("description") { +state.talk.description }

                div("speakers") {
                    state.talk.speakers.forEach { speaker ->
                        div(classes = "speakerCard") {
                            img(classes = "photo") { attrs.src = speaker.photoUrl }
                            h3 { +speaker.name }
                            span { +speaker.bio }
                            div("social") {
                                if (speaker.twitter.isNotBlank()) {
                                    a {
                                        attrs.href = speaker.twitter
                                        img { attrs.src = "https://image.freepik.com/free-icon/twitter-logo_318-40459.jpg" }
                                    }
                                }
                                if (speaker.linkedin.isNotBlank()) {
                                    a {
                                        attrs.href = speaker.linkedin
                                        attrs.target = "_blank"
                                        img { attrs.src = "https://png.icons8.com/metro/1600/linkedin.png" }
                                    }
                                }
                            }
                        }
                    }
                }

            }
        }
    }

    override fun showError(error: String) {
        println("Error: $error")
    }

    override fun showMessage(message: String) {
        println("Message: $message")
    }

    override fun getTalkId(): Int = props.talkId

    override fun showTalk(talk: Talk) {
        setState { this.talk = talk }
    }
}

interface DetailState : ScreenState {
    var talk: Talk
}

interface DetailProps : RProps {
    var talkId: Int
}


fun RBuilder.detail(talkId: Int) = child(DetailScreen::class) {
    attrs.talkId = talkId
}