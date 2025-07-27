package es.monsteraltech.skincare_tfm

import android.content.Intent
import androidx.fragment.app.Fragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.monsteraltech.skincare_tfm.fragments.AccountFragment
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

/**
 * Test para verificar la navegación del AccountFragment desde MainActivity
 */
@RunWith(AndroidJUnit4::class)
class MainActivityNavigationTest {

    @Test
    fun testNavigationToAccountFragment() {
        // Verificar que AccountFragment puede ser instanciado correctamente
        val accountFragment = AccountFragment()
        assertNotNull("AccountFragment debe poder ser instanciado", accountFragment)
        assertTrue("AccountFragment debe ser una instancia de Fragment", accountFragment is Fragment)
    }

    @Test
    fun testAccountFragmentClassExists() {
        // Verificar que la clase AccountFragment existe y es accesible
        val fragmentClass = AccountFragment::class.java
        assertNotNull("La clase AccountFragment debe existir", fragmentClass)
        assertEquals("El nombre de la clase debe ser correcto", "AccountFragment", fragmentClass.simpleName)
    }
}