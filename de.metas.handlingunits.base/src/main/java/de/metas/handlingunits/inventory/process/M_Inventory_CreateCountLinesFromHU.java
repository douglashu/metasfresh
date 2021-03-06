package de.metas.handlingunits.inventory.process;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Stream;

import org.adempiere.model.InterfaceWrapperHelper;
import org.adempiere.util.GuavaCollectors;
import org.adempiere.util.Services;
import org.adempiere.warehouse.WarehouseId;
import org.compiere.model.I_M_Inventory;

import de.metas.adempiere.model.I_M_Product;
import de.metas.handlingunits.IHUQueryBuilder;
import de.metas.handlingunits.IHUStatusBL;
import de.metas.handlingunits.IHandlingUnitsBL;
import de.metas.handlingunits.IHandlingUnitsDAO;
import de.metas.handlingunits.model.I_M_HU;
import de.metas.handlingunits.model.I_M_InventoryLine;
import de.metas.handlingunits.model.I_M_Locator;
import de.metas.handlingunits.storage.IHUProductStorage;
import de.metas.inventory.IInventoryDAO;
import de.metas.process.IProcessPrecondition;
import de.metas.process.IProcessPreconditionsContext;
import de.metas.process.JavaProcess;
import de.metas.process.Param;
import de.metas.process.ProcessPreconditionsResolution;
import lombok.NonNull;

/*
 * #%L
 * de.metas.handlingunits.base
 * %%
 * Copyright (C) 2018 metas GmbH
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

public class M_Inventory_CreateCountLinesFromHU extends JavaProcess implements IProcessPrecondition
{
	private final IHandlingUnitsDAO handlingUnitsDAO = Services.get(IHandlingUnitsDAO.class);
	private final IHUStatusBL huStatusBL = Services.get(IHUStatusBL.class);

	@Param(parameterName = I_M_Locator.COLUMNNAME_M_Locator_ID)
	private int locatorId;

	@Param(parameterName = I_M_Product.COLUMNNAME_M_Product_ID)
	private int productId;

	private I_M_Inventory _inventory;

	private Map<Integer, I_M_InventoryLine> inventoryLinesByHU;

	@Override
	public ProcessPreconditionsResolution checkPreconditionsApplicable(final IProcessPreconditionsContext context)
	{
		if (!context.isSingleSelection())
		{
			return ProcessPreconditionsResolution.rejectBecauseNotSingleSelection();
		}

		final I_M_Inventory inventory = context.getSelectedModel(I_M_Inventory.class);
		if (inventory.isProcessed())
		{
			return ProcessPreconditionsResolution.rejectWithInternalReason("inventory is processed");
		}

		return ProcessPreconditionsResolution.accept();
	}

	@Override
	protected String doIt()
	{
		inventoryLinesByHU = Services.get(IInventoryDAO.class).retrieveLinesForInventoryId(getInventory().getM_Inventory_ID(), I_M_InventoryLine.class)
				.stream()
				.filter(line -> line.getM_HU_ID() > 0)
				.collect(GuavaCollectors.toImmutableMapByKey(I_M_InventoryLine::getM_HU_ID));

		final long countInventoryLines = streamHUs()
				.flatMap(this::createUpdateInventoryLines)
				.count();

		return "@Created@/@Updated@ #" + countInventoryLines;
	}

	private I_M_Inventory getInventory()
	{
		if (_inventory == null)
		{
			_inventory = getRecord(I_M_Inventory.class);
		}
		return _inventory;
	}

	private Stream<I_M_HU> streamHUs()
	{
		final I_M_Inventory inventory = getInventory();

		final IHUQueryBuilder huQueryBuilder = handlingUnitsDAO.createHUQueryBuilder()
				.setOnlyTopLevelHUs()
				.addOnlyInWarehouseId(WarehouseId.ofRepoId(inventory.getM_Warehouse_ID()));

		if (locatorId > 0)
		{
			huQueryBuilder.addOnlyInLocatorId(locatorId);
		}
		if (productId > 0)
		{
			huQueryBuilder.addOnlyWithProductId(productId);
		}

		huQueryBuilder.addHUStatusesToInclude(huStatusBL.getQtyOnHandStatuses());

		return huQueryBuilder
				.createQuery()
				.iterateAndStream();
	}

	private Stream<I_M_InventoryLine> createUpdateInventoryLines(@NonNull final I_M_HU hu)
	{
		return Services.get(IHandlingUnitsBL.class)
				.getStorageFactory()
				.streamHUProductStorages(hu)
				.map(this::createUpdateInventoryLine);
	}

	private I_M_InventoryLine createUpdateInventoryLine(@NonNull final IHUProductStorage huProductStorage)
	{
		final I_M_Inventory inventory = getInventory();
		final I_M_InventoryLine inventoryLine;
		final I_M_HU hu = huProductStorage.getM_HU();

		//update line
		if (inventoryLinesByHU.containsKey(hu.getM_HU_ID()))
		{
			inventoryLine = inventoryLinesByHU.get(hu.getM_HU_ID());
		}
		// create line
		else
		{
			inventoryLine = InterfaceWrapperHelper.newInstance(I_M_InventoryLine.class);
			inventoryLine.setM_Inventory(inventory);
			inventoryLine.setAD_Org_ID(inventory.getAD_Org_ID());
			inventoryLine.setM_Product_ID(huProductStorage.getM_Product_ID());
			inventoryLine.setM_AttributeSetInstance_ID(0);

			inventoryLine.setM_Locator_ID(hu.getM_Locator_ID());
			inventoryLine.setM_HU_ID(hu.getM_HU_ID());
			inventoryLine.setM_HU_PI_Item_Product(null); // TODO
			inventoryLine.setQtyTU(BigDecimal.ZERO); // TODO

			inventoryLine.setC_UOM(huProductStorage.getC_UOM());
		}

		inventoryLine.setQtyBook(huProductStorage.getQty());
		inventoryLine.setQtyCount(huProductStorage.getQty());

		InterfaceWrapperHelper.save(inventoryLine);

		return inventoryLine;
	}
}
