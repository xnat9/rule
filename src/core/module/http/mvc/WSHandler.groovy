package core.module.http.mvc

abstract class WSHandler extends PathHandler {
    @Override
    String type() { WSHandler.simpleName }
}
