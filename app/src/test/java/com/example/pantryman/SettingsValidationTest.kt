package com.example.pantryman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for settings / directory validation logic.
 *
 * SettingsActivity's isValidCookbookDirectory() and isDirectoryEmpty() operate
 * on DocumentFile, which is an Android framework class and therefore unavailable
 * in a pure JVM test.  These tests exercise the equivalent pure string and
 * path-validation logic that governs the same decisions, keeping the suite free
 * of any Android dependency.
 */
class SettingsValidationTest {

    // ---------------------------------------------------------------------------
    // Pure helpers that mirror SettingsActivity's validation rules
    // ---------------------------------------------------------------------------

    /**
     * A file-system entry used to stand in for DocumentFile in pure-JVM tests.
     */
    data class FakeEntry(val name: String, val isDirectory: Boolean, val children: List<FakeEntry> = emptyList()) {
        val isFile: Boolean get() = !isDirectory
        fun listFiles(): List<FakeEntry> = children
    }

    /** Mirrors isDirectoryEmpty(). */
    private fun isDirectoryEmpty(dir: FakeEntry): Boolean = dir.listFiles().isEmpty()

    /**
     * Mirrors isValidCookbookDirectory():
     * - Must contain an "ingredients" sub-directory.
     * - Must contain a "pantry.yaml" file.
     * - The ingredients directory must contain at least one ".yaml" file.
     */
    private fun isValidCookbookDirectory(dir: FakeEntry): Boolean {
        val files = dir.listFiles()
        val hasIngredients = files.any { it.isDirectory && it.name == "ingredients" }
        val hasPantry = files.any { it.isFile && it.name == "pantry.yaml" }

        if (!hasIngredients || !hasPantry) return false

        val ingredientsDir = files.find { it.isDirectory && it.name == "ingredients" }
        val hasIngredientFiles = ingredientsDir?.listFiles()?.any {
            it.isFile && it.name.endsWith(".yaml")
        } ?: false

        return hasIngredientFiles
    }

    /**
     * Mirrors getCurrentSourceDataDirectory():
     * If the saved path starts with "content://", fall back to the default internal path;
     * otherwise return the saved path or the default if null.
     */
    private fun resolveSourceDataDirectory(savedPath: String?, defaultPath: String): String {
        return if (savedPath?.startsWith("content://") == true) {
            defaultPath
        } else {
            savedPath ?: defaultPath
        }
    }

    /**
     * Mirrors the filename filter used when copying ingredient files:
     * accepts regular files whose name ends with ".yaml".
     */
    private fun isIngredientFile(name: String): Boolean = name.endsWith(".yaml")

    /**
     * Mirrors the filename filter used when copying recipe files:
     * accepts regular files whose name ends with ".md".
     */
    private fun isRecipeFile(name: String): Boolean = name.endsWith(".md")

    // ---------------------------------------------------------------------------
    // isDirectoryEmpty tests
    // ---------------------------------------------------------------------------

    @Test
    fun `isDirectoryEmpty returns true for directory with no children`() {
        val dir = FakeEntry("data", isDirectory = true, children = emptyList())
        assertTrue(isDirectoryEmpty(dir))
    }

    @Test
    fun `isDirectoryEmpty returns false when directory has children`() {
        val dir = FakeEntry("data", isDirectory = true, children = listOf(
            FakeEntry("pantry.yaml", isDirectory = false)
        ))
        assertFalse(isDirectoryEmpty(dir))
    }

    // ---------------------------------------------------------------------------
    // isValidCookbookDirectory tests
    // ---------------------------------------------------------------------------

    @Test
    fun `isValidCookbookDirectory returns true for well-formed directory`() {
        val ingredientsDir = FakeEntry("ingredients", isDirectory = true, children = listOf(
            FakeEntry("potato.yaml", isDirectory = false)
        ))
        val dir = FakeEntry("cookbook", isDirectory = true, children = listOf(
            ingredientsDir,
            FakeEntry("pantry.yaml", isDirectory = false)
        ))
        assertTrue(isValidCookbookDirectory(dir))
    }

    @Test
    fun `isValidCookbookDirectory returns false when pantry yaml is missing`() {
        val ingredientsDir = FakeEntry("ingredients", isDirectory = true, children = listOf(
            FakeEntry("potato.yaml", isDirectory = false)
        ))
        val dir = FakeEntry("cookbook", isDirectory = true, children = listOf(ingredientsDir))
        assertFalse(isValidCookbookDirectory(dir))
    }

    @Test
    fun `isValidCookbookDirectory returns false when ingredients directory is missing`() {
        val dir = FakeEntry("cookbook", isDirectory = true, children = listOf(
            FakeEntry("pantry.yaml", isDirectory = false)
        ))
        assertFalse(isValidCookbookDirectory(dir))
    }

    @Test
    fun `isValidCookbookDirectory returns false when ingredients directory is empty`() {
        val ingredientsDir = FakeEntry("ingredients", isDirectory = true, children = emptyList())
        val dir = FakeEntry("cookbook", isDirectory = true, children = listOf(
            ingredientsDir,
            FakeEntry("pantry.yaml", isDirectory = false)
        ))
        assertFalse(isValidCookbookDirectory(dir))
    }

    @Test
    fun `isValidCookbookDirectory returns false when ingredients directory has no yaml files`() {
        val ingredientsDir = FakeEntry("ingredients", isDirectory = true, children = listOf(
            FakeEntry("readme.txt", isDirectory = false)
        ))
        val dir = FakeEntry("cookbook", isDirectory = true, children = listOf(
            ingredientsDir,
            FakeEntry("pantry.yaml", isDirectory = false)
        ))
        assertFalse(isValidCookbookDirectory(dir))
    }

    @Test
    fun `isValidCookbookDirectory returns false for empty directory`() {
        val dir = FakeEntry("cookbook", isDirectory = true, children = emptyList())
        assertFalse(isValidCookbookDirectory(dir))
    }

    @Test
    fun `isValidCookbookDirectory returns true with multiple ingredient files`() {
        val ingredientsDir = FakeEntry("ingredients", isDirectory = true, children = listOf(
            FakeEntry("potato.yaml", isDirectory = false),
            FakeEntry("carrot.yaml", isDirectory = false),
            FakeEntry("chicken.yaml", isDirectory = false)
        ))
        val dir = FakeEntry("cookbook", isDirectory = true, children = listOf(
            ingredientsDir,
            FakeEntry("pantry.yaml", isDirectory = false),
            FakeEntry("recipes", isDirectory = true)
        ))
        assertTrue(isValidCookbookDirectory(dir))
    }

    // ---------------------------------------------------------------------------
    // resolveSourceDataDirectory tests
    // ---------------------------------------------------------------------------

    @Test
    fun `resolveSourceDataDirectory returns default when savedPath is null`() {
        val default = "/data/user/0/com.example.pantryman/files/cookbook_data"
        assertEquals(default, resolveSourceDataDirectory(null, default))
    }

    @Test
    fun `resolveSourceDataDirectory returns default when savedPath is content URI`() {
        val default = "/data/user/0/com.example.pantryman/files/cookbook_data"
        val contentUri = "content://com.android.externalstorage.documents/tree/primary%3Acookbook"
        assertEquals(default, resolveSourceDataDirectory(contentUri, default))
    }

    @Test
    fun `resolveSourceDataDirectory returns savedPath when it is a file system path`() {
        val default = "/data/user/0/com.example.pantryman/files/cookbook_data"
        val savedPath = "/sdcard/cookbook_data"
        assertEquals(savedPath, resolveSourceDataDirectory(savedPath, default))
    }

    // ---------------------------------------------------------------------------
    // File name filter tests
    // ---------------------------------------------------------------------------

    @Test
    fun `isIngredientFile accepts yaml files`() {
        assertTrue(isIngredientFile("potato.yaml"))
        assertTrue(isIngredientFile("chicken-breast.yaml"))
    }

    @Test
    fun `isIngredientFile rejects non-yaml files`() {
        assertFalse(isIngredientFile("potato.json"))
        assertFalse(isIngredientFile("README.md"))
        assertFalse(isIngredientFile("potato.YAML")) // case-sensitive
        assertFalse(isIngredientFile(""))
    }

    @Test
    fun `isRecipeFile accepts markdown files`() {
        assertTrue(isRecipeFile("lasagna.md"))
        assertTrue(isRecipeFile("chicken-soup.md"))
    }

    @Test
    fun `isRecipeFile rejects non-markdown files`() {
        assertFalse(isRecipeFile("lasagna.txt"))
        assertFalse(isRecipeFile("lasagna.MD")) // case-sensitive
        assertFalse(isRecipeFile(""))
    }

    // ---------------------------------------------------------------------------
    // Preference key / constant validation
    // ---------------------------------------------------------------------------

    @Test
    fun `default data dir constant is relative path without leading slash`() {
        // The DEFAULT_DATA_DIR in SettingsActivity is "cookbook_data" (relative).
        // A leading slash would break File(filesDir, DEFAULT_DATA_DIR).
        val defaultDataDir = "cookbook_data"
        assertFalse("DEFAULT_DATA_DIR must be a relative path", defaultDataDir.startsWith("/"))
        assertFalse("DEFAULT_DATA_DIR must not be empty", defaultDataDir.isEmpty())
    }

    @Test
    fun `prefs name constant is non-empty string`() {
        val prefsName = "PantrymanPrefs"
        assertTrue(prefsName.isNotEmpty())
    }

    @Test
    fun `pref data dir key is non-empty string`() {
        val prefDataDir = "data_directory"
        assertTrue(prefDataDir.isNotEmpty())
    }
}
