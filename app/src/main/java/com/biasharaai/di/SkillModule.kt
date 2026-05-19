package com.biasharaai.di

import com.biasharaai.skills.BiasharaSkill
import com.biasharaai.skills.builtin.CalculateProfitSkill
import com.biasharaai.skills.builtin.DetectAnomalySkill
import com.biasharaai.skills.builtin.DraftMessageSkill
import com.biasharaai.skills.builtin.ExtractReceiptDataSkill
import com.biasharaai.skills.builtin.FindCopurchasePairsSkill
import com.biasharaai.skills.builtin.ForecastDemandSkill
import com.biasharaai.skills.builtin.PingSkill
import com.biasharaai.skills.builtin.QueryCustomersSkill
import com.biasharaai.skills.builtin.QueryInventorySkill
import com.biasharaai.skills.builtin.QueryLedgerByHourSkill
import com.biasharaai.skills.builtin.QueryLedgerSkill
import com.biasharaai.skills.builtin.QueryLedgerTrendSkill
import com.biasharaai.skills.builtin.QueryLedgerV2Skill
import com.biasharaai.skills.builtin.QuerySalesSkill
import com.biasharaai.skills.builtin.SuggestPriceSkill
import com.biasharaai.skills.builtin.QueryAppKnowledgeSkill
import com.biasharaai.skills.builtin.QueryBusinessProfileSkill
import com.biasharaai.skills.builtin.QueryServicesSkill
import com.biasharaai.skills.builtin.TeachUserSkill
import com.biasharaai.skills.builtin.TranscribeVoiceSkill
import com.biasharaai.skills.builtin.UpdateDataSkill
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
object SkillModule {

    @Provides @IntoSet fun providePingSkill(skill: PingSkill): BiasharaSkill = skill
    @Provides @IntoSet fun provideQuerySalesSkill(skill: QuerySalesSkill): BiasharaSkill = skill
    @Provides @IntoSet fun provideQueryLedgerSkill(skill: QueryLedgerSkill): BiasharaSkill = skill
    @Provides @IntoSet fun provideQueryLedgerV2Skill(skill: QueryLedgerV2Skill): BiasharaSkill = skill
    @Provides @IntoSet fun provideQueryLedgerByHourSkill(skill: QueryLedgerByHourSkill): BiasharaSkill = skill
    @Provides @IntoSet fun provideQueryLedgerTrendSkill(skill: QueryLedgerTrendSkill): BiasharaSkill = skill
    @Provides @IntoSet fun provideQueryInventorySkill(skill: QueryInventorySkill): BiasharaSkill = skill
    @Provides @IntoSet fun provideCalculateProfitSkill(skill: CalculateProfitSkill): BiasharaSkill = skill
    @Provides @IntoSet fun provideQueryCustomersSkill(skill: QueryCustomersSkill): BiasharaSkill = skill
    @Provides @IntoSet fun provideForecastDemandSkill(skill: ForecastDemandSkill): BiasharaSkill = skill
    @Provides @IntoSet fun provideSuggestPriceSkill(skill: SuggestPriceSkill): BiasharaSkill = skill
    @Provides @IntoSet fun provideDraftMessageSkill(skill: DraftMessageSkill): BiasharaSkill = skill
    @Provides @IntoSet fun provideUpdateDataSkill(skill: UpdateDataSkill): BiasharaSkill = skill
    @Provides @IntoSet fun provideExtractReceiptDataSkill(skill: ExtractReceiptDataSkill): BiasharaSkill = skill
    @Provides @IntoSet fun provideTranscribeVoiceSkill(skill: TranscribeVoiceSkill): BiasharaSkill = skill
    @Provides @IntoSet fun provideDetectAnomalySkill(skill: DetectAnomalySkill): BiasharaSkill = skill
    @Provides @IntoSet fun provideFindCopurchasePairsSkill(skill: FindCopurchasePairsSkill): BiasharaSkill = skill
    @Provides @IntoSet fun provideQueryAppKnowledgeSkill(skill: QueryAppKnowledgeSkill): BiasharaSkill = skill
    @Provides @IntoSet fun provideTeachUserSkill(skill: TeachUserSkill): BiasharaSkill = skill
    @Provides @IntoSet fun provideQueryBusinessProfileSkill(skill: QueryBusinessProfileSkill): BiasharaSkill = skill
    @Provides @IntoSet fun provideQueryServicesSkill(skill: QueryServicesSkill): BiasharaSkill = skill
}
