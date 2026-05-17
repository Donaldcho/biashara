package com.biasharaai.ui.insights

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.biasharaai.ui.ledger.LedgerFragment

class InsightsPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> InsightsOverviewFragment()
        1 -> CreditFragment()
        else -> LedgerFragment()
    }
}
