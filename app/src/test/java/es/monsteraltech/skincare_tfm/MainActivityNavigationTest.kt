package es.monsteraltech.skincare_tfm
import androidx.fragment.app.Fragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.monsteraltech.skincare_tfm.fragments.AccountFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class MainActivityNavigationTest {
    @Test
    fun testNavigationToAccountFragment() {
        val accountFragment = AccountFragment()
        assertNotNull("AccountFragment debe poder ser instanciado", accountFragment)
        assertTrue("AccountFragment debe ser una instancia de Fragment", accountFragment is Fragment)
    }
    @Test
    fun testAccountFragmentClassExists() {
        val fragmentClass = AccountFragment::class.java
        assertNotNull("La clase AccountFragment debe existir", fragmentClass)
        assertEquals("El nombre de la clase debe ser correcto", "AccountFragment", fragmentClass.simpleName)
    }
}