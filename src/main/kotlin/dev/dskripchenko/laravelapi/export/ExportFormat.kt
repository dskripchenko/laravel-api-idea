package dev.dskripchenko.laravelapi.export

/**
 * The formats `api:export` can produce for one endpoint.
 *
 * Named here so the menu and the scratch file agree on what they are dealing
 * with; the producing is the application's job entirely.
 */
enum class ExportFormat(val option: String, val label: String, val extension: String, val languageId: String?) {
    BRUNO("bruno", "Bruno request (.bru)", "bru", null),
    CURL("curl", "cURL command", "sh", null),
    HTTP("http", "HTTP Client request (.http)", "http", "HTTP Request"),
    POSTMAN("postman", "Postman collection", "json", "JSON"),
    MARKDOWN("markdown", "Markdown", "md", "Markdown");

    companion object {
        fun byLabel(label: String): ExportFormat? = entries.firstOrNull { it.label == label }
    }
}
