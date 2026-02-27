package com.example.pantryman

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.pantryman.databinding.ActivityMainBinding
import com.example.pantryman.databinding.DialogAddToPantryBinding
import com.example.pantryman.databinding.DialogCreateIngredientBinding
import com.example.pantryman.databinding.DialogPantryItemBinding
import com.example.pantryman.databinding.DialogSettingsBinding
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var engine: CookbookEngine
    private lateinit var pantryAdapter: PantryAdapter
    private lateinit var prefs: SharedPreferences
    private lateinit var syncDirPicker: ActivityResultLauncher<Uri?>
    @Volatile private var syncInProgress = false

    private var allIngredients = listOf<Ingredient>()
    private var searchQuery = ""

    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_NAME = "PantrymanPrefs"
        private const val PREF_SYNC_URI = "sync_uri"
        private const val DEFAULT_DATA_DIR = "cookbook_data"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        syncDirPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                prefs.edit().putString(PREF_SYNC_URI, uri.toString()).apply()
                runSyncIn {
                    reloadEngine()
                    Toast.makeText(this, R.string.msg_sync_folder_set, Toast.LENGTH_SHORT).show()
                }
            }
        }

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        setupRecyclerView()
        setupSearch()
        setupFab()
        initEngine()
    }

    override fun onResume() {
        super.onResume()
        if (::engine.isInitialized && getSyncUri() != null) {
            runSyncIn { reloadEngine() }
        }
    }

    override fun onPause() {
        super.onPause()
        if (::engine.isInitialized) Thread { syncToSAF() }.start()
    }

    private fun runSyncIn(onDone: () -> Unit) {
        if (syncInProgress) return
        syncInProgress = true
        Thread {
            try {
                syncFromSAF()
            } finally {
                syncInProgress = false
            }
            runOnUiThread(onDone)
        }.start()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> { showSettingsDialog(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ── Engine ───────────────────────────────────────────────────────────────

    private fun initEngine() {
        try {
            val dataPath = getAppDataDir()
            File(dataPath).mkdirs()
            setupInitialData(File(dataPath))
            engine = CookbookEngine(dataPath)
            loadData()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize engine", e)
            Toast.makeText(this, "Failed to start: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadData() {
        allIngredients = engine.getAllIngredients()
        updateDisplay()
    }

    private fun updateDisplay() {
        val query = searchQuery.trim()
        val pantryItems = allIngredients
            .filter { it.isInPantry }
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
            .sortedWith(compareBy({ it.category }, { it.name }))

        pantryAdapter.submitList(pantryItems)
        binding.emptyState.visibility = if (pantryItems.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (pantryItems.isEmpty()) View.GONE else View.VISIBLE
    }

    // ── UI setup ─────────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        pantryAdapter = PantryAdapter(onItemClick = { ingredient -> showPantryItemDialog(ingredient) })
        binding.recyclerView.adapter = pantryAdapter
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) binding.fabAdd.shrink() else binding.fabAdd.extend()
            }
        })
    }

    private fun setupSearch() {
        binding.searchInput.addTextChangedListener { text ->
            searchQuery = text?.toString() ?: ""
            updateDisplay()
        }
    }

    private fun setupFab() {
        binding.fabAdd.setOnClickListener { showAddToPantryDialog() }
    }

    // ── Dialogs ──────────────────────────────────────────────────────────────

    private fun showAddToPantryDialog() {
        val db = DialogAddToPantryBinding.inflate(layoutInflater)
        val sorted = allIngredients.sortedWith(compareBy({ it.category }, { it.name }))
        var filtered = sorted

        var outerDialog: AlertDialog? = null
        val pickerAdapter = IngredientPickerAdapter { ingredient ->
            outerDialog?.dismiss()
            showPantryItemDialog(ingredient)
        }
        db.recyclerView.adapter = pickerAdapter
        db.recyclerView.layoutManager = LinearLayoutManager(this)
        pickerAdapter.submitList(filtered)

        db.searchInput.addTextChangedListener { text ->
            val q = text?.toString() ?: ""
            filtered = if (q.isBlank()) sorted else sorted.filter {
                it.name.contains(q, ignoreCase = true) || it.category.contains(q, ignoreCase = true)
            }
            pickerAdapter.submitList(filtered)
        }

        db.btnCreateNew.setOnClickListener {
            outerDialog?.dismiss()
            showCreateIngredientDialog()
        }

        outerDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.title_add_to_pantry)
            .setView(db.root)
            .setNegativeButton(R.string.btn_cancel) { d, _ -> d.dismiss() }
            .create()
        outerDialog!!.show()
    }

    private fun showPantryItemDialog(ingredient: Ingredient) {
        val db = DialogPantryItemBinding.inflate(layoutInflater)
        val units = listOf("", "kg", "g", "lb", "oz", "pieces", "cups", "tbsp", "tsp", "ml", "l", "fl oz")
        db.autoUnit.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, units))

        if (ingredient.isInPantry) {
            ingredient.quantity?.let { db.editQuantity.setText(it) }
            ingredient.quantityType?.let { unit ->
                val idx = units.indexOf(unit)
                if (idx >= 0) db.autoUnit.setText(units[idx], false)
            }
            db.btnRemove.visibility = View.VISIBLE
        }

        var dialog: AlertDialog? = null

        db.btnRemove.setOnClickListener {
            engine.updatePantryStatus(ingredient.name, false)
            loadData()
            dialog?.dismiss()
            Toast.makeText(this, "${ingredient.name} removed from pantry", Toast.LENGTH_SHORT).show()
        }

        val positiveLabel = if (ingredient.isInPantry) "Update" else getString(R.string.btn_add_to_pantry)
        dialog = MaterialAlertDialogBuilder(this)
            .setTitle(ingredient.name.replaceFirstChar { it.uppercase() })
            .setMessage(ingredient.category)
            .setView(db.root)
            .setPositiveButton(positiveLabel) { _, _ ->
                val qty = db.editQuantity.text?.toString()?.toDoubleOrNull()
                val unit = db.autoUnit.text?.toString()?.takeIf { it.isNotEmpty() }
                val success = engine.updatePantryStatus(ingredient.name, true, qty, unit)
                if (success) {
                    loadData()
                    val msg = if (ingredient.isInPantry) "Updated ${ingredient.name}" else "${ingredient.name} added to pantry"
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to update pantry", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.btn_cancel) { d, _ -> d.dismiss() }
            .create()
        dialog!!.show()
    }

    private fun showCreateIngredientDialog() {
        val db = DialogCreateIngredientBinding.inflate(layoutInflater)
        val categories = engine.getAllCategories()
        db.autoCategory.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories))

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.title_create_ingredient)
            .setView(db.root)
            .setPositiveButton("Create") { _, _ ->
                val name = db.editName.text?.toString()?.trim() ?: ""
                val category = db.autoCategory.text?.toString()?.trim() ?: ""
                val tags = db.editTags.text?.toString()
                    ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                    ?: emptyList()

                if (name.isEmpty() || category.isEmpty()) {
                    Toast.makeText(this, "Name and category are required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val success = engine.createIngredient(name, category, null, tags)
                if (success) {
                    allIngredients = engine.getAllIngredients()
                    val newIngredient = allIngredients.find { it.name.equals(name, ignoreCase = true) }
                    if (newIngredient != null) {
                        showPantryItemDialog(newIngredient)
                    } else {
                        loadData()
                    }
                } else {
                    Toast.makeText(this, "Failed to create ingredient", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.btn_cancel) { d, _ -> d.dismiss() }
            .show()
    }

    private fun showSettingsDialog() {
        val db = DialogSettingsBinding.inflate(layoutInflater)
        db.textSyncUri.text = getSyncUri()?.toString() ?: getString(R.string.sync_folder_none)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.title_settings)
            .setView(db.root)
            .setNegativeButton(R.string.btn_cancel) { d, _ -> d.dismiss() }
            .create()

        db.btnChooseSyncFolder.setOnClickListener {
            dialog.dismiss()
            syncDirPicker.launch(getSyncUri())
        }

        db.btnSyncNow.setOnClickListener {
            dialog.dismiss()
            runSyncIn {
                reloadEngine()
                syncToSAF()
                Toast.makeText(this, R.string.sync_complete, Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    // ── Sync ─────────────────────────────────────────────────────────────────

    private fun getSyncUri(): Uri? {
        return prefs.getString(PREF_SYNC_URI, null)?.let { Uri.parse(it) }
    }

    private fun reloadEngine() {
        try {
            engine.cleanup()
            engine = CookbookEngine(getAppDataDir())
            loadData()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reload engine", e)
        }
    }

    private fun syncFromSAF() {
        try {
        val syncUri = getSyncUri() ?: return
        val syncDir = DocumentFile.fromTreeUri(this, syncUri) ?: return
        val localDir = File(getAppDataDir())

        // pantry.yaml: copy SAF → local
        syncDir.findFile("pantry.yaml")?.uri?.let { uri ->
            contentResolver.openInputStream(uri)?.use { input ->
                File(localDir, "pantry.yaml").outputStream().use { input.copyTo(it) }
            }
        }

        // ingredients/: full mirror (copy new/updated, delete removed)
        val localIngredients = File(localDir, "ingredients").also { it.mkdirs() }
        val safIngredients = syncDir.findFile("ingredients")
        if (safIngredients != null) {
            val safFiles = safIngredients.listFiles().filter { it.name?.endsWith(".yaml") == true }
            val safNames = safFiles.mapNotNull { it.name }.toSet()
            // Copy SAF → local
            safFiles.forEach { file ->
                contentResolver.openInputStream(file.uri)?.use { input ->
                    File(localIngredients, file.name!!).outputStream().use { input.copyTo(it) }
                }
            }
            // Delete local files not in SAF (mirror deletions from desktop)
            localIngredients.listFiles()
                ?.filter { it.name !in safNames }
                ?.forEach { it.delete() }
        }
        Log.d(TAG, "Sync from SAF complete")
        } catch (e: Exception) {
            Log.e(TAG, "Sync from SAF failed", e)
        }
    }

    private fun syncToSAF() {
        try {
        val syncUri = getSyncUri() ?: return
        val syncDir = DocumentFile.fromTreeUri(this, syncUri) ?: return
        val localDir = File(getAppDataDir())

        // pantry.yaml: copy local → SAF
        val pantryFile = File(localDir, "pantry.yaml")
        if (pantryFile.exists()) {
            val safPantry = syncDir.findFile("pantry.yaml")
                ?: syncDir.createFile("application/x-yaml", "pantry.yaml")
            safPantry?.uri?.let { uri ->
                contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    pantryFile.inputStream().copyTo(out)
                }
            }
        }

        // ingredients/: copy local → SAF (mirror deletions too)
        val localIngredients = File(localDir, "ingredients")
        if (localIngredients.exists()) {
            val safIngredients = syncDir.findFile("ingredients")
                ?: syncDir.createDirectory("ingredients")
            val localNames = localIngredients.listFiles()?.mapNotNull { it.name }?.toSet() ?: emptySet()
            // Copy local → SAF
            localIngredients.listFiles()?.forEach { localFile ->
                val safFile = safIngredients?.findFile(localFile.name)
                    ?: safIngredients?.createFile("application/x-yaml", localFile.name)
                safFile?.uri?.let { uri ->
                    contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        localFile.inputStream().copyTo(out)
                    }
                }
            }
            // Delete SAF files not in local (mirror deletions from Android)
            safIngredients?.listFiles()
                ?.filter { it.name !in localNames }
                ?.forEach { it.delete() }
        }
        Log.d(TAG, "Sync to SAF complete")
        } catch (e: Exception) {
            Log.e(TAG, "Sync to SAF failed", e)
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun getAppDataDir(): String {
        return "${filesDir.absolutePath}/$DEFAULT_DATA_DIR"
    }

    private fun setupInitialData(dataDir: File) {
        if (dataDir.exists() && dataDir.listFiles()?.isNotEmpty() == true) return
        try {
            Log.d(TAG, "Copying initial data from assets")
            File(dataDir, "ingredients").mkdirs()
            File(dataDir, "recipes").mkdirs()
            copyAsset("pantry.yaml", File(dataDir, "pantry.yaml"))
            assets.list("ingredients")?.forEach { name ->
                copyAsset("ingredients/$name", File(dataDir, "ingredients/$name"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup initial data", e)
        }
    }

    private fun copyAsset(assetPath: String, dest: File) {
        try {
            assets.open(assetPath).use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not copy asset $assetPath: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::engine.isInitialized) engine.cleanup()
    }
}
