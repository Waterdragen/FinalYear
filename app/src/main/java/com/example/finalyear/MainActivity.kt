@file:Suppress("UNUSED_VARIABLE")

package com.example.finalyear

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.finalyear.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding
    val bottomNavTable: HashMap<Int, PageOrder> = hashMapOf(
        R.id.navSurvey to PageOrder(0, PageSurvey()),
        R.id.navSurveyStore to PageOrder(1, PageSurveyStore()),
//        R.id.navMap to PageOrder(2, PageMap()),
    )
    var currentNavId = R.id.navSurvey

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.hideActionBar()

        binding = ActivityMainBinding.inflate(layoutInflater)
        this.setContentView(binding.root)
        this.initFragment()

        binding.bottomNav.setOnItemSelectedListener { item ->
            val newNavId = item.itemId
            if (newNavId == currentNavId) {
                return@setOnItemSelectedListener false
            }
            if (!bottomNavTable.contains(newNavId)) {
                return@setOnItemSelectedListener false
            }
            slideFragment(newNavId)
            true
        }
    }

    private fun hideActionBar() {
        supportActionBar?.hide()
    }

    // Choose which fragment to be the home
    private fun initFragment() {
        val page = bottomNavTable[currentNavId]!!  // currentNavId is always valid
        supportFragmentManager
            .beginTransaction()
            .add(R.id.frameLayout, page.fragment)
            .commit()
    }

    private fun slideFragment(newNavId: Int) {
        val currentPage = bottomNavTable[currentNavId]!!  // currentNavId is always valid
        val newPage = bottomNavTable[newNavId]!!  // newNavId is always valid
        val shouldSlideRight = currentPage.order < newPage.order
        val currentFragment = currentPage.fragment
        val newFragment = newPage.fragment

        val transaction = supportFragmentManager.beginTransaction()
        if (shouldSlideRight) {
            transaction.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
        } else {
            transaction.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right)
        }
        transaction.hide(currentFragment)
        if (newFragment.isAdded) {
            // New fragment is already loaded
            transaction.show(newFragment)
        } else {
            // Load and cache new fragment
            transaction.add(R.id.frameLayout, newFragment)
        }
        currentNavId = newNavId
        transaction.commit()
    }

    data class PageOrder(val order: Int, val fragment: Fragment)
}