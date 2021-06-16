package pro.panopticon.client.model

class Status(
    var environment: String,
    var system: String,
    var component: String,
    var server: String,
    var measurements: List<Measurement?>,
) {
    constructor(componentInfo: ComponentInfo, measurements: List<Measurement?>) : this(componentInfo.environment,
        componentInfo.system,
        componentInfo.component,
        componentInfo.server,
        measurements)

    override fun toString(): String {
        return "UpdatedStatus{" +
               "environment='" + environment + '\'' +
               ", system='" + system + '\'' +
               ", component='" + component + '\'' +
               ", server='" + server + '\'' +
               ", measurements=" + measurements +
               '}'
    }
}
