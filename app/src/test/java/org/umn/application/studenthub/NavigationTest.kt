package org.umn.application.studenthub

import org.junit.Assert.assertEquals
import org.junit.Test
import org.umn.application.studenthub.ui.navigation.Screen

class NavigationTest {
    @Test fun primaryDestinationsMatchFigma() {
        assertEquals(listOf("Home", "Jadwal", "Profil"), Screen.bottomNavItems.map { it.title })
    }
}
