package org.umn.application.studenthub.data.local

data class CurriculumCourse(
    val code: String,
    val title: String,
    val sks: Int,
    val semester: Int,
    val isElective: Boolean = false
)

object UmnCurriculum {
    val courses = listOf(
        CurriculumCourse("IF111", "Ethics in Informatics", 2, 1),
        CurriculumCourse("IF120", "Discrete Mathematics", 3, 1),
        CurriculumCourse("IF130", "Programming Fundamentals", 3, 1),
        CurriculumCourse("CE121", "Linear Algebra", 3, 1),
        CurriculumCourse("CE232", "Digital Systems", 3, 1),
        CurriculumCourse("UM122", "English 1", 2, 1),
        CurriculumCourse("UM142", "Indonesian Language", 2, 1),
        CurriculumCourse("UM152", "Religion", 2, 1),

        CurriculumCourse("IF233", "Algorithms & Data Structure", 3, 2),
        CurriculumCourse("IF260", "Operating System", 3, 2),
        CurriculumCourse("CE319", "Probability and Statistics", 3, 2),
        CurriculumCourse("CE332", "Computer Architecture & Organization", 3, 2),
        CurriculumCourse("IF221", "Calculus", 4, 2),
        CurriculumCourse("UM162", "Pancasila", 2, 2),
        CurriculumCourse("UM223", "English 2", 2, 2),

        CurriculumCourse("IF333", "Formal Languages & Automata Theory", 3, 3),
        CurriculumCourse("IF350", "Software Engineering & Project Management", 3, 3),
        CurriculumCourse("IF351", "Database Systems", 3, 3),
        CurriculumCourse("IF352", "Basic Web Programming", 3, 3),
        CurriculumCourse("IF440", "Artificial Intelligence", 3, 3),
        CurriculumCourse("UM163", "Civics", 2, 3),
        CurriculumCourse("MSC3401", "Public Speaking & Presentation Skills", 3, 3),

        CurriculumCourse("IF431", "Algorithms & Complexity", 3, 4),
        CurriculumCourse("IF433", "Object Oriented Programming", 3, 4),
        CurriculumCourse("IF441", "Machine Learning", 3, 4),
        CurriculumCourse("IF450", "Human Computer Interaction", 3, 4),
        CurriculumCourse("IF451", "Advanced Web Programming", 3, 4),
        CurriculumCourse("IF470", "Computer Security", 3, 4),
        CurriculumCourse("CE449", "Computer Network", 2, 4),

        CurriculumCourse("IF542", "Deep Learning", 3, 5),
        CurriculumCourse("IF570", "Mobile Application Programming", 3, 5),
        CurriculumCourse("IF571", "Cybersecurity", 3, 5),
        CurriculumCourse("IF581", "Game Development", 3, 5),
        CurriculumCourse("IF590", "Information Technology Research", 2, 5),
        CurriculumCourse("EM105", "Technopreneurship", 3, 5),
        CurriculumCourse("UM321", "English 3", 2, 5),

        CurriculumCourse("IF670", "Mobile Cross-Platform Programming", 3, 6, isElective = true),
        CurriculumCourse("IF675", "Blockchain Technologies", 2, 6, isElective = true),
        CurriculumCourse("IF680", "Multimedia Concept", 3, 6, isElective = true),
        CurriculumCourse("IS411", "Data Modelling", 2, 6, isElective = true),
        CurriculumCourse("IS412", "Data Visualization", 2, 6, isElective = true),
        CurriculumCourse("IS513", "E-Business", 2, 6, isElective = true),
        CurriculumCourse("CE542", "System Administration", 2, 6, isElective = true),
        CurriculumCourse("CE739", "Pervasive Computing", 3, 6, isElective = true),
        CurriculumCourse("IF6XY", "MBKM", 20, 6, isElective = true),

        CurriculumCourse("IF6XY", "Internship Track 1", 20, 7),

        CurriculumCourse("IF800", "Final Project", 6, 8)
    )

    fun forSemester(sem: Int): List<CurriculumCourse> =
        courses.filter { it.semester == sem && !it.isElective }

    fun electives(): List<CurriculumCourse> =
        courses.filter { it.isElective }

    fun otherSemesters(currentSem: Int): List<CurriculumCourse> =
        courses.filter { it.semester != currentSem && !it.isElective }
}
