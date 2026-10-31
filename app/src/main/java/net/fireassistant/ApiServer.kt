package net.fireassistant

import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Endpoints (GET or POST), all require `Authorization: Bearer <token>` or `?token=<token>`:
 *   /api/wake
 *   /api/sleep
 *   /api/unlock
 *   /api/update
 *   /api/ha
 *   /api/ha/dashboard?path=lovelace/kitchen
 */
class ApiServer(
    port: Int,
    private val token: String,
    private val actions: Actions,
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        if (session.method != Method.GET && session.method != Method.POST) {
            return json(405, false, "method not allowed")
        }
        if (!authorized(session)) return json(401, false, "unauthorized")

        val result = when (session.uri.trimEnd('/')) {
            "/api/wake" -> actions.wake()
            "/api/sleep" -> actions.sleep()
            "/api/unlock" -> actions.unlock()
            "/api/update" -> actions.update()
            "/api/ha" -> actions.openHomeAssistant()
            "/api/ha/dashboard" -> actions.openDashboard(session.parameters["path"]?.firstOrNull().orEmpty())
            else -> ActionResult(false, "not found", 404)
        }
        return json(result.httpStatus, result.ok, result.message)
    }

    private fun authorized(session: IHTTPSession): Boolean {
        val supplied = session.headers["authorization"]?.removePrefix("Bearer ")?.trim()
            ?: session.parameters["token"]?.firstOrNull()
            ?: return false
        return MessageDigest.isEqual(supplied.toByteArray(), token.toByteArray())
    }

    private fun json(status: Int, ok: Boolean, message: String): Response {
        val body = JSONObject().put("ok", ok).put("message", message).toString()
        return newFixedLengthResponse(Response.Status.lookup(status) ?: Response.Status.INTERNAL_ERROR, "application/json", body)
    }
}
