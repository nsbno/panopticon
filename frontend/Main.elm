module Main exposing (..)

import App.Messages exposing (..)
import App.Model exposing (..)
import App.Subscriptions exposing (subscriptions)
import App.Update exposing (update)
import App.View exposing (view)
import Components.Model
import Detail.Model
import Nav.Model exposing (Page)
import Nav.Nav exposing (hashParser, toHash)
import Navigation
import Ports


initModel : AppState -> Page -> Model
initModel appState page =
    Model appState Components.Model.init Detail.Model.init page


init : Flags -> Navigation.Location -> ( Model, Cmd Msg )
init flags location =
    let
        page =
            hashParser location

        appState =
            toAppState flags

        initialCommand =
            Maybe.withDefault (Ports.login ()) <| Maybe.map (\_ -> Navigation.newUrl <| toHash page) appState.token
    in
    ( initModel appState page, initialCommand )


toAppState : Flags -> AppState
toAppState flags =
    AppState flags.url (toMaybe flags.token)


toMaybe : String -> Maybe String
toMaybe s =
    if s == "" then
        Nothing

    else
        Just s


main : Program Flags Model Msg
main =
    Navigation.programWithFlags (UpdateUrl << hashParser)
        { init = init
        , update = update
        , view = view
        , subscriptions = subscriptions
        }
