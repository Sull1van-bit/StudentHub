package org.umn.application.studenthub.ui.academic

import org.junit.Assert.*
import org.junit.Test

class AcademicStateTest {
    @Test fun addedTasksAccumulateAndSurviveReload() {
        val saved = mutableMapOf<String, String>()
        val state = AcademicState(saved::get) { key, value -> saved[key] = value }
        assertFalse(state.addTask("IF570", "  "))
        assertTrue(state.addTask("IF570", "Latihan tambahan"))
        assertTrue(state.addTask("IF570", "Review materi"))
        assertEquals(12, state.totalFor("IF570"))
        assertEquals(6, state.activeTaskCount)
        state.toggleTask(state.tasks.last().id)
        val restored = AcademicState(saved::get) { key, value -> saved[key] = value }
        assertEquals(2, restored.tasks.count { it.detail == "Tugas lokal" })
        assertEquals(9, restored.completedFor("IF570"))
        assertEquals(5, restored.activeTaskCount)
    }
    @Test fun gradeAndGoalEditsValidateAndPersist() {
        val saved = mutableMapOf<String, String>()
        val state = AcademicState(saved::get) { key, value -> saved[key] = value }
        assertEquals(3.5, state.semesterGpa, 0.001)
        assertFalse(state.updateGrade("IF570", "101", "A"))
        assertTrue(state.updateGrade("IF570", "80", "B+"))
        assertEquals(3.375, state.semesterGpa, 0.001)
        assertFalse(state.updateGoal("4,01"))
        assertTrue(state.updateGoal("3,75"))
        val restored = AcademicState(saved::get) { key, value -> saved[key] = value }
        assertEquals(3.75, restored.goal, 0.001)
        assertEquals("B+", restored.grades.getValue("IF570").letter)
    }

    @Test fun completingTaskUpdatesCourseProgressAndActiveCount() {
        val state = AcademicState()
        assertEquals(4, state.activeTaskCount)
        assertEquals(8, state.completedFor("IF570"))
        state.toggleTask("navigation")
        assertEquals(3, state.activeTaskCount)
        assertEquals(9, state.completedFor("IF570"))
        state.toggleTask("navigation")
        assertEquals(4, state.activeTaskCount)
        assertEquals(8, state.completedFor("IF570"))
    }

    @Test fun semester5ActiveTasksCountMatchesTasksAndDecrementsOnToggle() {
        val prefs = mutableMapOf("onboarding_completed" to "true")
        val state = AcademicState(prefs::get) { k, v -> prefs[k] = v }
        assertEquals(7, state.currentCourses().size)
        assertEquals(4, state.activeTaskCount)
        assertEquals(8, state.completedFor("IF570"))
        assertEquals(3, state.completedFor("IF590"))
        assertEquals(4, state.completedFor("IF542"))
        state.toggleTask("research-prop")
        assertEquals(3, state.activeTaskCount)
        assertEquals(4, state.completedFor("IF590"))
        state.toggleTask("research-prop")
        assertEquals(4, state.activeTaskCount)
    }
}
