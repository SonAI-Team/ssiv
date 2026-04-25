package com.sonai.ssiv.test

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

abstract class AbstractPagesActivity protected constructor(
    private val title: Int,
    private val layout: Int,
    private val notes: List<Page>
) : AppCompatActivity() {

    private var pageNum: Int = 0

    protected open fun onPageChanged(page: Int) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(layout)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.appbar)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top, v.paddingRight, v.paddingBottom)
            insets
        }

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            setTitle(this@AbstractPagesActivity.title)
            setDisplayHomeAsUpEnabled(true)
        }
        findViewById<View>(R.id.next).setOnClickListener { next() }
        findViewById<View>(R.id.previous).setOnClickListener { previous() }
        if (savedInstanceState != null && savedInstanceState.containsKey(BUNDLE_PAGE)) {
            pageNum = savedInstanceState.getInt(BUNDLE_PAGE)
        }
    }

    override fun onResume() {
        super.onResume()
        updateNotes()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(BUNDLE_PAGE, pageNum)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun next() {
        pageNum++
        updateNotes()
    }

    private fun previous() {
        pageNum--
        updateNotes()
    }

    private fun updateNotes() {
        if (pageNum > notes.size - 1) {
            return
        }
        supportActionBar?.subtitle = getString(notes[pageNum].subtitle)
        findViewById<TextView>(R.id.note).setText(notes[pageNum].text)
        findViewById<View>(R.id.next).visibility = if (pageNum >= notes.size - 1) View.INVISIBLE else View.VISIBLE
        findViewById<View>(R.id.previous).visibility = if (pageNum <= 0) View.INVISIBLE else View.VISIBLE
        onPageChanged(pageNum)
    }

    protected fun getPage(): Int {
        return pageNum
    }

    companion object {
        private const val BUNDLE_PAGE = "page"
    }
}
