package app

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element

class AndroidManifestUninstallTest {
    @Test
    fun theUninstallDialogOffersToKeepAppData() {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val manifest = factory.newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        val application = manifest.getElementsByTagName("application").item(0) as Element

        assertEquals(
            "true",
            application.getAttributeNS("http://schemas.android.com/apk/res/android", "hasFragileUserData"),
        )
    }
}
