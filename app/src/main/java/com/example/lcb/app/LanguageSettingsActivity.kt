package com.example.lcb.app

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lcb.app.databinding.ItemLanguageOptionBinding
import com.example.lcb.app.databinding.LayoutLanguageSettingsBinding

class LanguageSettingsActivity : AppCompatActivity() {
    private lateinit var binding: LayoutLanguageSettingsBinding
    private lateinit var languageAdapter: LanguageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = LayoutLanguageSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        binding.languageToolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.languageToolbar.setNavigationOnClickListener {
            finish()
        }

        languageAdapter = LanguageAdapter(::onLanguageSelected)
        binding.languageRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@LanguageSettingsActivity)
            adapter = languageAdapter
        }
        languageAdapter.submitList(
            languages = supportedLanguages(),
            selectedLanguageTag = AppLocaleStore.currentLanguageTag(this)
        )
    }

    private fun onLanguageSelected(language: AppLanguage) {
        val currentTag = AppLocaleStore.currentLanguageTag(this)
        if (language.tag == currentTag) return

        AppLocaleStore.saveLanguageTag(this, language.tag)
        AppLocaleStore.applyLanguageTag(language.tag)
        languageAdapter.updateSelectedLanguage(language.tag)
        setResult(RESULT_OK)
    }

    private fun supportedLanguages(): List<AppLanguage> = listOf(
        AppLanguage(R.string.language_english, "en"),
        AppLanguage(R.string.language_chinese, "zh-CN"),
        AppLanguage(R.string.language_hindi, "hi"),
        AppLanguage(R.string.language_spanish, "es"),
        AppLanguage(R.string.language_arabic, "ar"),
        AppLanguage(R.string.language_portuguese, "pt"),
        AppLanguage(R.string.language_indonesian, "id"),
        AppLanguage(R.string.language_russian, "ru"),
        AppLanguage(R.string.language_french, "fr"),
        AppLanguage(R.string.language_japanese, "ja"),
        AppLanguage(R.string.language_korean, "ko")
    )

    private class LanguageAdapter(
        private val onLanguageClick: (AppLanguage) -> Unit
    ) : RecyclerView.Adapter<LanguageAdapter.LanguageViewHolder>() {
        private val languages = mutableListOf<AppLanguage>()
        private var selectedLanguageTag = AppLocaleStore.DEFAULT_LANGUAGE_TAG

        fun submitList(languages: List<AppLanguage>, selectedLanguageTag: String) {
            this.languages.clear()
            this.languages.addAll(languages)
            this.selectedLanguageTag = selectedLanguageTag
            notifyDataSetChanged()
        }

        fun updateSelectedLanguage(selectedLanguageTag: String) {
            this.selectedLanguageTag = selectedLanguageTag
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LanguageViewHolder {
            val binding = ItemLanguageOptionBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return LanguageViewHolder(binding)
        }

        override fun onBindViewHolder(holder: LanguageViewHolder, position: Int) {
            holder.bind(languages[position])
        }

        override fun getItemCount(): Int = languages.size

        private inner class LanguageViewHolder(
            private val binding: ItemLanguageOptionBinding
        ) : RecyclerView.ViewHolder(binding.root) {
            fun bind(language: AppLanguage) {
                val isSelected = language.tag == selectedLanguageTag
                binding.languageName.setText(language.labelRes)
                binding.languageName.setTextColor(
                    if (isSelected) 0xFF6275FF.toInt() else 0xFF333333.toInt()
                )
                binding.languageName.typeface = Typeface.create(
                    "sans-serif",
                    if (isSelected) Typeface.BOLD else Typeface.NORMAL
                )
                binding.languageSelectedIcon.visibility = if (isSelected) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
                binding.languageOptionContainer.setBackgroundResource(
                    if (isSelected) {
                        R.drawable.bg_language_option_selected
                    } else {
                        R.drawable.bg_language_option_unselected
                    }
                )
                binding.languageOptionContainer.setOnClickListener {
                    onLanguageClick(language)
                }
            }
        }
    }
}
