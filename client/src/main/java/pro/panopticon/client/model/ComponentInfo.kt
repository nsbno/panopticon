package pro.panopticon.client.model

class ComponentInfo(var environment: String, var system: String, var component: String, var server: String) {
    override fun toString(): String {
        return "ComponentInfo{" +
               "environment='" + environment + '\'' +
               ", system='" + system + '\'' +
               ", component='" + component + '\'' +
               ", server='" + server + '\'' +
               '}'
    }
}
