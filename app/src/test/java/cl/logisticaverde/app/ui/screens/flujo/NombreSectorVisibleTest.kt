package cl.logisticaverde.app.ui.screens.flujo

import org.junit.Assert.assertEquals
import org.junit.Test

class NombreSectorVisibleTest {

    @Test
    fun `Quita la superficie entre parentesis al final del nombre`() {
        assertEquals("Sector 1 - Norte", nombreSectorVisible("Sector 1 - Norte (2.000 m²)"))
        assertEquals("Sector 2 - Centro Norte", nombreSectorVisible("Sector 2 - Centro Norte (2.000 m²)"))
        assertEquals("Sector X", nombreSectorVisible("Sector X (500 M2)"))
    }

    @Test
    fun `Deja intacto un nombre sin superficie o con otros parentesis`() {
        assertEquals("Sector PRUEBA", nombreSectorVisible("Sector PRUEBA"))
        assertEquals("Sector 5 (rocas)", nombreSectorVisible("Sector 5 (rocas)"))
    }
}
