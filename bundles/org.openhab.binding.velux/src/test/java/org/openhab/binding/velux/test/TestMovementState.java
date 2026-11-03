/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.velux.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.binding.velux.internal.things.VeluxExistingProducts;
import org.openhab.binding.velux.internal.things.VeluxKLFAPI.Command;
import org.openhab.binding.velux.internal.things.VeluxProduct;
import org.openhab.binding.velux.internal.things.VeluxProduct.MovementState;
import org.openhab.binding.velux.internal.things.VeluxProduct.ProductBridgeIndex;
import org.openhab.binding.velux.internal.things.VeluxProduct.ProductState;
import org.openhab.binding.velux.internal.things.VeluxProductName;
import org.openhab.binding.velux.internal.things.VeluxProductPosition;

/**
 * Tests movement-state derivation from KLF 200 product notifications.
 */
@NonNullByDefault
public class TestMovementState {
    private static final int OPEN_POSITION = 0x1000;
    private static final int CLOSED_POSITION = 0x9000;

    @Test
    public void testDoneReportsStopped() {
        VeluxProduct product = product(ProductState.DONE.value, OPEN_POSITION, CLOSED_POSITION);

        assertEquals(MovementState.STOPPED, product.getMovementState(false));
    }

    @Test
    public void testExecutingTowardSmallerPositionReportsUp() {
        VeluxProduct product = product(ProductState.EXECUTING.value, CLOSED_POSITION, OPEN_POSITION);

        assertEquals(MovementState.UP, product.getMovementState(false));
    }

    @Test
    public void testExecutingTowardLargerPositionReportsDown() {
        VeluxProduct product = product(ProductState.EXECUTING.value, OPEN_POSITION, CLOSED_POSITION);

        assertEquals(MovementState.DOWN, product.getMovementState(false));
    }

    @Test
    public void testCapturedDownwardSequenceRemainsDownUntilDone() {
        assertEquals(MovementState.DOWN, product(44, 0x1323, 0xC800).getMovementState(false));
        assertEquals(MovementState.DOWN, product(44, 0xBE47, 0xC800).getMovementState(false));
        assertEquals(MovementState.STOPPED, product(45, 0xC800, 0xC800).getMovementState(false));
    }

    @Test
    public void testInversionSwapsMovementDirection() {
        VeluxProduct product = product(ProductState.EXECUTING.value, CLOSED_POSITION, OPEN_POSITION);

        assertEquals(MovementState.DOWN, product.getMovementState(true));
    }

    @Test
    public void testExecutingWithNoPositionDifferenceReportsUnknown() {
        VeluxProduct product = product(ProductState.EXECUTING.value, OPEN_POSITION, OPEN_POSITION);

        assertEquals(MovementState.UNKNOWN, product.getMovementState(false));
    }

    @Test
    public void testExecutingWithInvalidPositionReportsUnknown() {
        VeluxProduct product = product(ProductState.EXECUTING.value, VeluxProductPosition.VPP_VELUX_UNKNOWN,
                OPEN_POSITION);

        assertEquals(MovementState.UNKNOWN, product.getMovementState(false));
    }

    @Test
    public void testUnconfirmedStatesReportUnknown() {
        for (ProductState state : new ProductState[] { ProductState.NON_EXECUTING, ProductState.ERROR,
                ProductState.NOT_USED, ProductState.WAITING_FOR_POWER, ProductState.UNKNOWN }) {
            VeluxProduct product = product(state.value, CLOSED_POSITION, OPEN_POSITION);

            assertEquals(MovementState.UNKNOWN, product.getMovementState(false), state.name());
        }
    }

    @Test
    public void testManualExecutingStateReportsUnknown() {
        VeluxProduct product = product(ProductState.MANUAL.value | ProductState.EXECUTING.value, CLOSED_POSITION,
                OPEN_POSITION);

        assertEquals(MovementState.UNKNOWN, product.getMovementState(false));
    }

    @Test
    public void testManualDoneStateReportsUnknown() {
        VeluxProduct product = product(ProductState.MANUAL.value | ProductState.DONE.value, CLOSED_POSITION,
                OPEN_POSITION);

        assertEquals(MovementState.UNKNOWN, product.getMovementState(false));
    }

    @Test
    public void testManualExecutingNotificationInvalidatesMovement() {
        VeluxExistingProducts products = existingProducts(ProductState.DONE.value, CLOSED_POSITION, CLOSED_POSITION);

        products.update(houseStatusProduct(ProductState.MANUAL.value | ProductState.EXECUTING.value, CLOSED_POSITION,
                OPEN_POSITION));

        assertEquals(MovementState.UNKNOWN, products.getMovementState(new ProductBridgeIndex(1), false));
    }

    @Test
    public void testNotUsedNotificationInvalidatesOnlyMovement() {
        VeluxExistingProducts products = existingProducts(ProductState.EXECUTING.value, CLOSED_POSITION, OPEN_POSITION);

        products.update(houseStatusProduct(ProductState.NOT_USED.value, OPEN_POSITION, OPEN_POSITION));

        VeluxProduct cachedProduct = products.get(new ProductBridgeIndex(1));
        assertEquals(ProductState.EXECUTING.value, cachedProduct.getState());
        assertEquals(CLOSED_POSITION, cachedProduct.getCurrentPosition());
        assertEquals(MovementState.UNKNOWN, products.getMovementState(new ProductBridgeIndex(1), false));
    }

    @Test
    public void testStatusRequestDoesNotOverwriteHouseStatusMovement() {
        VeluxExistingProducts products = existingProducts(ProductState.DONE.value, CLOSED_POSITION, CLOSED_POSITION);
        products.update(houseStatusProduct(ProductState.EXECUTING.value, CLOSED_POSITION, OPEN_POSITION));

        products.update(
                new VeluxProduct(VeluxProductName.UNKNOWN, new ProductBridgeIndex(1), ProductState.EXECUTING.value,
                        CLOSED_POSITION, VeluxProductPosition.VPP_VELUX_IGNORE, null, Command.GW_STATUS_REQUEST_REQ));

        assertEquals(MovementState.UP, products.getMovementState(new ProductBridgeIndex(1), false));
    }

    @Test
    public void testMovementCacheCanBeInvalidatedWithoutChangingProductState() {
        VeluxExistingProducts products = existingProducts(ProductState.EXECUTING.value, CLOSED_POSITION, OPEN_POSITION);
        products.update(houseStatusProduct(ProductState.EXECUTING.value, CLOSED_POSITION, OPEN_POSITION));

        products.invalidateMovementStates();

        assertEquals(ProductState.EXECUTING.value, products.get(new ProductBridgeIndex(1)).getState());
        assertEquals(MovementState.UNKNOWN, products.getMovementState(new ProductBridgeIndex(1), false));
    }

    private VeluxProduct product(int state, int currentPosition, int targetPosition) {
        return new VeluxProduct(VeluxProductName.UNKNOWN, new ProductBridgeIndex(1), state, currentPosition,
                targetPosition, null, Command.GW_NODE_STATE_POSITION_CHANGED_NTF);
    }

    private VeluxProduct houseStatusProduct(int state, int currentPosition, int targetPosition) {
        return new VeluxProduct(VeluxProductName.UNKNOWN, new ProductBridgeIndex(1), state, currentPosition,
                targetPosition, null, Command.GW_OPENHAB_RECEIVEONLY);
    }

    private VeluxExistingProducts existingProducts(int state, int currentPosition, int targetPosition) {
        VeluxExistingProducts products = new VeluxExistingProducts();
        products.register(product(state, currentPosition, targetPosition));
        products.resetDirtyFlag();
        return products;
    }
}
