package de.metas.material.dispo.commons.repository;

import static de.metas.material.event.EventTestHelper.ATTRIBUTE_SET_INSTANCE_ID;
import static de.metas.material.event.EventTestHelper.BEFORE_NOW;
import static de.metas.material.event.EventTestHelper.BPARTNER_ID;
import static de.metas.material.event.EventTestHelper.PRODUCT_ID;
import static de.metas.material.event.EventTestHelper.WAREHOUSE_ID;
import static java.math.BigDecimal.TEN;
import static org.adempiere.model.InterfaceWrapperHelper.newInstance;
import static org.adempiere.model.InterfaceWrapperHelper.save;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

import org.adempiere.test.AdempiereTestHelper;
import org.adempiere.test.AdempiereTestWatcher;
import org.compiere.util.TimeUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import de.metas.material.dispo.commons.repository.AvailableToPromiseMultiQuery.AvailableToPromiseMultiQueryBuilder;
import de.metas.material.dispo.model.I_MD_Candidate;
import de.metas.material.dispo.model.I_MD_Candidate_ATP_QueryResult;
import de.metas.material.dispo.model.X_MD_Candidate;
import de.metas.material.event.EventTestHelper;
import de.metas.material.event.commons.AttributesKey;
import de.metas.material.event.commons.MaterialDescriptor;
import de.metas.material.event.commons.ProductDescriptor;

/*
 * #%L
 * metasfresh-material-dispo-commons
 * %%
 * Copyright (C) 2017 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

public class AvailableToPromiseRepositoryTest
{
	private static final AttributesKey STORAGE_ATTRIBUTES_KEY = AttributesKey.ofAttributeValueIds(1, 2);

	public static final Date BEFORE_BEFORE_NOW = TimeUtil.addMinutes(BEFORE_NOW, -10);

	public static final BigDecimal TWENTY = new BigDecimal("20");

	private AvailableToPromiseRepository availableToPromiseRepository;

	@Rule
	public AdempiereTestWatcher adempiereTestWatcher = new AdempiereTestWatcher();

	@Before
	public void init()
	{
		AdempiereTestHelper.get().init();

		availableToPromiseRepository = new AvailableToPromiseRepository();
	}

	private MaterialDescriptor createMaterialDescriptor()
	{
		final ProductDescriptor productDescriptor = ProductDescriptor.forProductAndAttributes(
				PRODUCT_ID,
				STORAGE_ATTRIBUTES_KEY,
				ATTRIBUTE_SET_INSTANCE_ID);
		final MaterialDescriptor materialDescriptor = EventTestHelper
				.createMaterialDescriptor()
				.withProductDescriptor(productDescriptor);
		return materialDescriptor;
	}

	/**
	 * The stock record without bpartnerId is created after, so its Qty is not contained in the stock record with bpartner; therefore we count both.
	 */
	@Test
	public void retrieveAvailableStock_for_material_descriptor()
	{
		createStockRecord(BPARTNER_ID, BEFORE_NOW);
		createStockRecord(0, BEFORE_NOW); // belongs to "any" bpartner
		createStockRecord(BPARTNER_ID + 10, BEFORE_NOW); // belongs to an unrelated bPartner

		final MaterialDescriptor materialDescriptor = createMaterialDescriptor();
		assertThat(materialDescriptor.getCustomerId()).isEqualTo(BPARTNER_ID); // guard

		final AvailableToPromiseMultiQuery query = AvailableToPromiseMultiQuery.forDescriptorAndAllPossibleBPartnerIds(materialDescriptor);
		assertThat(query.getQueries()).hasSize(2); // guard

		final BigDecimal result = availableToPromiseRepository.retrieveAvailableStockQtySum(query);

		assertThat(result).isEqualByComparingTo(TWENTY);
	}

	/**
	 * The stock record without bpartnerId is created first, but it's date is after the one with bPartnerId.
	 * So its Qty is not contained in the stock record with bpartner; therefore we count both
	 */
	@Test
	public void retrieveAvailableStock_for_material_descriptor_2()
	{
		createStockRecord(0, BEFORE_NOW); // belongs to "any" bpartner
		createStockRecord(BPARTNER_ID, BEFORE_BEFORE_NOW);
		createStockRecord(BPARTNER_ID + 10, BEFORE_NOW); // belongs to an unrelated bPartner

		final MaterialDescriptor materialDescriptor = createMaterialDescriptor();

		final AvailableToPromiseMultiQuery query = AvailableToPromiseMultiQuery.forDescriptorAndAllPossibleBPartnerIds(materialDescriptor);

		final BigDecimal result = availableToPromiseRepository.retrieveAvailableStockQtySum(query);

		assertThat(result).isEqualByComparingTo(TWENTY);
	}

	/**
	 * The stock record without bpartnerId is created first, so it's qty is already included in the stock record with bpartner.
	 * Therefore we only count the stock record with bpartner.
	 */
	@Test
	public void retrieveAvailableStock_for_material_descriptor_3()
	{
		createStockRecord(0, BEFORE_NOW); // belongs to "any" bpartner
		createStockRecord(BPARTNER_ID, BEFORE_NOW);
		createStockRecord(BPARTNER_ID + 10, BEFORE_NOW); // belongs to an unrelated bPartner

		final MaterialDescriptor materialDescriptor = createMaterialDescriptor();
		final AvailableToPromiseMultiQuery query = AvailableToPromiseMultiQuery.forDescriptorAndAllPossibleBPartnerIds(materialDescriptor);

		final BigDecimal result = availableToPromiseRepository.retrieveAvailableStockQtySum(query);

		assertThat(result).isEqualByComparingTo(TEN);
	}

	/**
	 * If the query has no bpartnerId, then only records with no bpartnerId may be counted.
	 */
	@Test
	public void retrieveAvailableStock_for_material_descriptor_4()
	{
		createStockRecord(0, BEFORE_NOW); // belongs to "any" bpartner
		createStockRecord(BPARTNER_ID, BEFORE_NOW);
		createStockRecord(BPARTNER_ID + 10, BEFORE_NOW); // belongs to an unrelated bPartner

		final MaterialDescriptor materialDescriptor = createMaterialDescriptor().withCustomerId(0);
		final AvailableToPromiseMultiQuery query = AvailableToPromiseMultiQuery.of(AvailableToPromiseQuery.forMaterialDescriptor(materialDescriptor));

		final BigDecimal result = availableToPromiseRepository.retrieveAvailableStockQtySum(query);

		assertThat(result).isEqualByComparingTo(TEN);
	}

	/**
	 * The stock record "for any bpartner" is created first, but it's date is after the one with {@code BPARTNER_ID}.
	 * So its qty is not contained within the stock record with {@code {@code BPARTNER_ID}}.
	 * Therefore for {@code BPARTNER_ID} we count both.
	 * However, for {@code BPARTNER_ID + 10}, we do not count the "for any bpartner"-record, because it's assumed to be included in {@code BPARTNER_ID + 10}'s record.
	 */
	@Test
	public void retrieveAvailableStock_differentBPartners_addToPredefinedBuckets()
	{
		retrieveAvailableStock_differentBPartners_performTest(true);
	}

	@Test
	public void retrieveAvailableStock_differentBPartners_doNot_addToPredefinedBuckets()
	{
		retrieveAvailableStock_differentBPartners_performTest(false);
	}

	private void retrieveAvailableStock_differentBPartners_performTest(final boolean addToPredefinedBuckets)
	{
		createStockRecord(0, BEFORE_NOW); // belongs to "any" bpartner
		createStockRecord(BPARTNER_ID, BEFORE_BEFORE_NOW);
		createStockRecord(BPARTNER_ID + 10, BEFORE_NOW);

		final AvailableToPromiseMultiQueryBuilder multiQueryBuilder = AvailableToPromiseMultiQuery
				.builder()
				.addToPredefinedBuckets(addToPredefinedBuckets);

		final AvailableToPromiseQuery query1 = AvailableToPromiseQuery
				.builder()
				.productId(PRODUCT_ID)
				.storageAttributesKey(STORAGE_ATTRIBUTES_KEY)
				.bpartnerId(BPARTNER_ID)
				.build();
		multiQueryBuilder.query(query1);

		final AvailableToPromiseQuery query2 = AvailableToPromiseQuery
				.builder()
				.productId(PRODUCT_ID)
				.storageAttributesKey(STORAGE_ATTRIBUTES_KEY)
				.bpartnerId(BPARTNER_ID + 10)
				.build();
		multiQueryBuilder.query(query2);

		final AvailableToPromiseMultiQuery multipQuery = multiQueryBuilder.build();

		final AvailableToPromiseResult result = availableToPromiseRepository.retrieveAvailableStock(multipQuery);

		assertThat(result).isNotNull();

		final List<AvailableToPromiseResultGroup> resultGroups = result.getResultGroups();
		assertThat(resultGroups).hasSize(2);

		assertThat(resultGroups)
				.allSatisfy(group -> assertThat(group.getProductId()).isEqualTo(PRODUCT_ID));

		assertThat(resultGroups)
				.filteredOn(group -> group.getBpartnerId() == BPARTNER_ID)
				.hasSize(1)
				.allSatisfy(group -> assertThat(group.getQty()).isEqualByComparingTo(TWENTY));

		assertThat(resultGroups)
				.filteredOn(group -> group.getBpartnerId() == BPARTNER_ID + 10)
				.hasSize(1)
				.allSatisfy(group -> assertThat(group.getQty()).isEqualByComparingTo(TEN));
	}

	private int seqNoCounter = 1; // we start with one, because 0 is not considered valid by the code under test

	private I_MD_Candidate_ATP_QueryResult createStockRecord(
			final int bPartnerId,
			final Date dateProjected)
	{
		// set only the values we need
		final I_MD_Candidate candidateRecord = newInstance(I_MD_Candidate.class);
		candidateRecord.setDateProjected(new Timestamp(dateProjected.getTime()));
		candidateRecord.setIsActive(true);
		candidateRecord.setMD_Candidate_Type(X_MD_Candidate.MD_CANDIDATE_TYPE_STOCK);
		candidateRecord.setSeqNo(seqNoCounter);
		save(candidateRecord);

		final I_MD_Candidate_ATP_QueryResult viewRecord = newInstance(I_MD_Candidate_ATP_QueryResult.class);
		viewRecord.setM_Product_ID(PRODUCT_ID);
		viewRecord.setM_Warehouse_ID(WAREHOUSE_ID);
		viewRecord.setC_BPartner_Customer_ID(bPartnerId);
		viewRecord.setDateProjected(new Timestamp(dateProjected.getTime()));
		viewRecord.setStorageAttributesKey(STORAGE_ATTRIBUTES_KEY.getAsString());
		viewRecord.setQty(BigDecimal.TEN);
		viewRecord.setSeqNo(seqNoCounter);
		save(viewRecord);

		seqNoCounter++;

		return viewRecord;
	}
}
