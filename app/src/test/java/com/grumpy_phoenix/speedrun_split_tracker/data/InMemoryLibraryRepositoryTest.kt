package com.grumpy_phoenix.speedrun_split_tracker.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InMemoryLibraryRepositoryTest {

    private val repo = InMemoryLibraryRepository()

    @Test
    fun listsOnlyDirectChildrenSortedByName() = runBlocking {
        val game = repo.createFolder(null, "Mario")
        repo.createFolder(game, "b-practice")
        repo.createFolder(game, "A-categories")
        repo.createRunFile(game, "16 Star")
        repo.createRunFile(null, "Top level")

        assertEquals(listOf("Mario"), repo.folders(null).first().map { it.name })
        assertEquals(listOf("A-categories", "b-practice"), repo.folders(game).first().map { it.name })
        assertEquals(listOf("16 Star"), repo.runFiles(game).first().map { it.name })
        assertEquals(listOf("Top level"), repo.runFiles(null).first().map { it.name })
    }

    @Test
    fun renamesFoldersAndRunFiles() = runBlocking {
        val folder = repo.createFolder(null, "Old")
        val file = repo.createRunFile(folder, "Old file")
        repo.renameFolder(folder, "New")
        repo.renameRunFile(file, "New file")

        assertEquals("New", repo.folder(folder)?.name)
        assertEquals("New file", repo.runFile(file)?.name)
    }

    @Test
    fun deletingAFolderDeletesEverythingInside() = runBlocking {
        val game = repo.createFolder(null, "Game")
        val sub = repo.createFolder(game, "Sub")
        val deep = repo.createFolder(sub, "Deep")
        val file = repo.createRunFile(deep, "Any%")
        val other = repo.createFolder(null, "Other")

        repo.deleteFolder(game)

        assertNull(repo.folder(sub))
        assertNull(repo.folder(deep))
        assertNull(repo.runFile(file))
        assertEquals(listOf(other), repo.folders(null).first().map { it.id })
    }
}
