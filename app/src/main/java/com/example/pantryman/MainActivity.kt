package com.example.pantryman

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Paint
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
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.example.pantryman.databinding.ActivityMainBinding
import com.example.pantryman.databinding.DialogAddToPantryBinding
import com.example.pantryman.databinding.DialogCreateIngredientBinding
import com.example.pantryman.databinding.DialogPantryItemBinding
import com.example.pantryman.databinding.DialogSettingsBinding
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var engine: JanusEngine
    private lateinit var pantryAdapter: PantryAdapter
    private lateinit var prefs: SharedPreferences
    private lateinit var syncDirPicker: ActivityResultLauncher<Uri?>
    @Volatile private var syncInProgress = false

    private var allIngredients = listOf<Ingredient>()
    private var searchQuery = ""

    /**
     * Set to true just before launching a child activity (e.g. ShoppingModeActivity).
     * When true, onResume skips the SAF pull so that writes made by the child activity
     * are not overwritten before they are pushed on the next onPause.
     */
    private var returningFromChildActivity = false

    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_NAME = "PantrymanPrefs"
        private const val PREF_SYNC_URI = "sync_uri"
        private const val DEFAULT_DATA_DIR = "cookbook_data"
        const val EXTRA_DATA_DIR = "extra_data_dir"
        const val EXTRA_DEVICE_ID = "extra_device_id"
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
        if (!::engine.isInitialized) return

        if (returningFromChildActivity) {
            // Coming back from ShoppingModeActivity (or another child): the child has
            // already written to local files. Reload the engine from local storage so
            // we see those changes, but do NOT pull from SAF — that would overwrite them
            // before onPause gets a chance to push them back up.
            returningFromChildActivity = false
            reloadEngine()
        } else if (getSyncUri() != null) {
            // Genuine resume from background: pull latest from SAF then reload.
            runSyncIn { reloadEngine() }
        } else {
            loadData()
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
            R.id.action_shopping_mode -> { openShoppingMode(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ── Engine ───────────────────────────────────────────────────────────────

    private fun initEngine() {
        try {
            val dataPath = getAppDataDir()
            File(dataPath).mkdirs()
            setupInitialData(File(dataPath))
            engine = JanusEngine(dataPath, pantrymanDeviceId())
            loadData()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize engine", e)
            Toast.makeText(this, getString(R.string.msg_failed_to_start, e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun loadData() {
        allIngredients = engine.getAllIngredients()
        updateDisplay()
    }

    private fun updateDisplay() {
        val query = searchQuery.trim()
        val filtered = allIngredients
            .filter { it.isInPantry }
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true)
                                      || it.category.contains(query, ignoreCase = true) }
            .sortedWith(compareBy({ it.category.ifEmpty { "\uFFFF" } }, { it.name }))

        pantryAdapter.submitList(buildSectionedList(filtered))
        binding.emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun buildSectionedList(ingredients: List<Ingredient>): List<PantryListItem> {
        val result = mutableListOf<PantryListItem>()
        var currentCat: String? = null
        for (ing in ingredients) {
            val cat = ing.category.ifEmpty { getString(R.string.category_uncategorised) }
            if (cat != currentCat) {
                result.add(PantryListItem.Header(cat))
                currentCat = cat
            }
            result.add(PantryListItem.Item(ing))
        }
        return result
    }

    // ── UI setup ─────────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        pantryAdapter = PantryAdapter(onItemClick = { ingredient -> showPantryItemDialog(ingredient) })
        binding.recyclerView.adapter = pantryAdapter
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.addItemDecoration(PantryItemDivider())
        binding.recyclerView.addItemDecoration(StickyHeaderDecoration(
            adapter = pantryAdapter,
            headerViewType = PantryAdapter.VIEW_TYPE_HEADER,
            getHeaderLabel = { pos ->
                (pantryAdapter.currentList.getOrNull(pos) as? PantryListItem.Header)?.category ?: ""
            },
        ))
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) binding.fabAdd.shrink() else binding.fabAdd.extend()
            }
        })

        // Attach swipe gestures
        val swipeCb = PantrySwipeCallback(
            context = this,
            onSwipeLeft = { ingredient -> handleSwipeRemove(ingredient) },
            onSwipeRight = { ingredient -> handleSwipeTouch(ingredient) },
            getIngredientAt = { pos -> pantryAdapter.getIngredientAt(pos) }
        )
        ItemTouchHelper(swipeCb).attachToRecyclerView(binding.recyclerView)
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

    // ── Swipe handlers ────────────────────────────────────────────────────────

    /**
     * Swipe-left: immediately remove the ingredient from the pantry, show an undo snackbar.
     */
    private fun handleSwipeRemove(ingredient: Ingredient) {
        engine.updatePantryStatus(ingredient.name, false)
        loadData()

        Snackbar.make(
            binding.root,
            getString(R.string.msg_removed_swipe, ingredient.name.replaceFirstChar { it.uppercase() }),
            Snackbar.LENGTH_LONG
        ).setAction(R.string.action_undo) {
            // Re-add with original quantity/unit
            engine.updatePantryStatus(
                ingredient.name,
                true,
                ingredient.quantity?.toDoubleOrNull(),
                ingredient.quantityType
            )
            loadData()
        }.show()
    }

    /**
     * Swipe-right: touch (refresh timestamp of) the pantry item — "I still have this".
     */
    private fun handleSwipeTouch(ingredient: Ingredient) {
        engine.touchPantryItem(ingredient.name)
        loadData()

        Snackbar.make(
            binding.root,
            getString(R.string.msg_still_have_it, ingredient.name.replaceFirstChar { it.uppercase() }),
            Snackbar.LENGTH_SHORT
        ).show()
    }

    // ── Shopping Mode ─────────────────────────────────────────────────────────

    private fun openShoppingMode() {
        returningFromChildActivity = true
        val intent = Intent(this, ShoppingModeActivity::class.java).apply {
            putExtra(EXTRA_DATA_DIR, getAppDataDir())
            putExtra(EXTRA_DEVICE_ID, pantrymanDeviceId())
        }
        startActivity(intent)
    }

    // ── Dialogs ──────────────────────────────────────────────────────────────

    private fun showAddToPantryDialog() {
        val db = DialogAddToPantryBinding.inflate(layoutInflater)
        val sorted = allIngredients.sortedBy { it.name.lowercase() }
        var filtered = sorted
        var currentQuery = ""

        var outerDialog: AlertDialog? = null
        val pickerAdapter = IngredientPickerAdapter(
            onItemClick = { ingredient ->
                outerDialog?.dismiss()
                showPantryItemDialog(ingredient)
            },
            onCreateNew = { nameHint ->
                outerDialog?.dismiss()
                showCreateIngredientDialog(nameHint)
            },
        )
        db.recyclerView.adapter = pickerAdapter
        db.recyclerView.layoutManager = LinearLayoutManager(this)
        pickerAdapter.submitList(filtered, currentQuery)

        db.searchInput.addTextChangedListener { text ->
            currentQuery = text?.toString() ?: ""
            filtered = if (currentQuery.isBlank()) sorted else sorted.filter {
                it.name.contains(currentQuery, ignoreCase = true) ||
                it.category.contains(currentQuery, ignoreCase = true)
            }
            pickerAdapter.submitList(filtered, currentQuery)
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

        val positiveLabel = if (ingredient.isInPantry) getString(R.string.btn_update) else getString(R.string.btn_add_to_pantry)
        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(ingredient.name.replaceFirstChar { it.uppercase() })
            .setView(db.root)
            .setPositiveButton(positiveLabel) { _, _ ->
                val (qty, unit) = parseQuantityString(db.editQuantity.text?.toString() ?: "")
                val success = engine.updatePantryStatus(ingredient.name, true, qty, unit)
                if (success) {
                    loadData()
                    val msg = if (ingredient.isInPantry) getString(R.string.msg_updated, ingredient.name) else getString(R.string.msg_added_to_pantry, ingredient.name)
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, R.string.msg_failed_to_update_pantry, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)

        // Only show category subtitle if non-empty
        if (ingredient.category.isNotEmpty()) builder.setMessage(ingredient.category)

        // Destructive action lives in the neutral button slot (left side of action row)
        if (ingredient.isInPantry) {
            builder.setNeutralButton(R.string.btn_remove_from_pantry) { _, _ ->
                engine.updatePantryStatus(ingredient.name, false)
                loadData()
                Toast.makeText(this, getString(R.string.msg_removed_from_pantry, ingredient.name), Toast.LENGTH_SHORT).show()
            }
        }

        val dialog = builder.create()
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.setOnShowListener {
            // Pre-fill AFTER the view is laid out — setting text before show() prevents
            // TextInputLayout from floating the label, causing text/label overlap.
            if (ingredient.isInPantry) {
                val prefill = formatQuantityString(ingredient.quantity?.toDoubleOrNull(), ingredient.quantityType)
                db.editQuantity.setText(prefill)
                db.editQuantity.setSelection(prefill.length)
                val tv = android.util.TypedValue()
                theme.resolveAttribute(com.google.android.material.R.attr.colorError, tv, true)
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(tv.data)
            }
            db.editQuantity.requestFocus()
        }
        dialog.show()
    }

    private fun showCreateIngredientDialog(nameHint: String = "") {
        val db = DialogCreateIngredientBinding.inflate(layoutInflater)
        val categories = engine.getAllCategories()
        db.autoCategory.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories))
        if (nameHint.isNotEmpty()) db.editName.setText(nameHint)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.title_create_ingredient)
            .setView(db.root)
            .setPositiveButton(R.string.btn_create) { _, _ ->
                val name = db.editName.text?.toString()?.trim() ?: ""
                val category = db.autoCategory.text?.toString()?.trim() ?: ""
                val plural = db.editPlural.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                val tags = db.editTags.text?.toString()
                    ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                    ?: emptyList()

                if (name.isEmpty()) {
                    Toast.makeText(this, R.string.msg_name_required, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val success = engine.createIngredient(name, category, tags, plural)
                if (success) {
                    allIngredients = engine.getAllIngredients()
                    val newIngredient = allIngredients.find { it.name.equals(name, ignoreCase = true) }
                    if (newIngredient != null) {
                        showPantryItemDialog(newIngredient)
                    } else {
                        loadData()
                    }
                } else {
                    Toast.makeText(this, R.string.msg_failed_to_create_ingredient, Toast.LENGTH_SHORT).show()
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

    /** Returns a stable per-device ID, generating and persisting one on first call. */
    private fun pantrymanDeviceId(): String {
        val key = "device_id"
        var id = prefs.getString(key, null)
        if (id == null) {
            id = "android-" + java.util.UUID.randomUUID().toString().replace("-", "").take(12)
            prefs.edit().putString(key, id).apply()
        }
        return id
    }

    private fun reloadEngine() {
        try {
            engine.cleanup()
            engine = JanusEngine(getAppDataDir(), pantrymanDeviceId())
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

        // Per-device pantry files: copy every pantry.*.yaml from SAF → local
        // (includes the legacy pantry.yaml if present; engine reads it as read-only).
        syncDir.listFiles()
            .filter { file ->
                val n = file.name ?: return@filter false
                n.startsWith("pantry.") && n.endsWith(".yaml")
            }
            .forEach { file ->
                try {
                    contentResolver.openInputStream(file.uri)?.use { input ->
                        File(localDir, file.name!!).outputStream().use { input.copyTo(it) }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping pantry file ${file.name}: ${e.message}")
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
                try {
                    contentResolver.openInputStream(file.uri)?.use { input ->
                        File(localIngredients, file.name!!).outputStream().use { input.copyTo(it) }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping ingredient file ${file.name}: ${e.message}")
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

        // Per-device pantry: push only this device's pantry.{device-id}.yaml to SAF.
        // Never touch other devices' files or the legacy pantry.yaml.
        val devicePantryName = "pantry.${pantrymanDeviceId()}.yaml"
        val devicePantryFile = File(localDir, devicePantryName)
        if (devicePantryFile.exists()) {
            val safPantry = syncDir.findFile(devicePantryName)
                ?: syncDir.createFile("application/x-yaml", devicePantryName)
            safPantry?.uri?.let { uri ->
                contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    devicePantryFile.inputStream().copyTo(out)
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

    // ── Decorations ──────────────────────────────────────────────────────────

    /**
     * Draws a 1dp divider at the bottom of every ingredient row.
     * Category header rows are skipped — the header's own paddingTop provides separation.
     */
    private inner class PantryItemDivider : RecyclerView.ItemDecoration() {
        private val paint = Paint().apply {
            val ta = obtainStyledAttributes(intArrayOf(com.google.android.material.R.attr.colorOutlineVariant))
            color = ta.getColor(0, 0x1FFFFFFF)
            ta.recycle()
            strokeWidth = resources.displayMetrics.density  // 1dp
        }

        override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            val left = parent.paddingLeft.toFloat()
            val right = (parent.width - parent.paddingRight).toFloat()
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                val pos = parent.getChildAdapterPosition(child)
                if (pos != RecyclerView.NO_POSITION &&
                    parent.adapter?.getItemViewType(pos) == PantryAdapter.VIEW_TYPE_ITEM) {
                    val y = (child.bottom + child.translationY).toFloat()
                    c.drawLine(left, y, right, y, paint)
                }
            }
        }
    }
}
