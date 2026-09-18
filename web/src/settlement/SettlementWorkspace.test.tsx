import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';

vi.mock('../api/client', () => ({
  ApiError: class ApiError extends Error {},
  listMySales: vi.fn(),
  recordSaleShipment: vi.fn(),
  simulateSalePayment: vi.fn(),
}));

import { listMySales, recordSaleShipment, type Sale, simulateSalePayment } from '../api/client';
import { SettlementWorkspace } from './SettlementWorkspace';

const paymentPendingBuyerSale: Sale = {
  id: 'sale-41',
  auctionId: 'auction-41',
  item: { id: 'item-41', title: 'Signed settlement card' },
  buyer: { handle: 'buyer_41' },
  seller: { handle: 'seller_41' },
  participantRole: 'BUYER',
  amountCents: 12_000,
  state: 'PAYMENT_PENDING',
  createdAt: '2026-09-18T12:00:00Z',
  paymentDeadlineAt: '2026-09-19T12:00:00Z',
};

describe('SettlementWorkspace', () => {
  afterEach(cleanup);

  beforeEach(() => {
    vi.mocked(listMySales).mockReset();
    vi.mocked(recordSaleShipment).mockReset();
    vi.mocked(simulateSalePayment).mockReset();
  });

  test('lets the buyer simulate payment by the durable deadline', async () => {
    vi.mocked(listMySales).mockResolvedValue([paymentPendingBuyerSale]);
    vi.mocked(simulateSalePayment).mockResolvedValue({
      ...paymentPendingBuyerSale,
      state: 'SHIPMENT_PENDING',
      paidAt: '2026-09-18T12:15:00Z',
      shipmentDeadlineAt: '2026-09-21T12:15:00Z',
    });

    render(<SettlementWorkspace />);

    expect(await screen.findByText('Signed settlement card')).toBeInTheDocument();
    expect(screen.getByText('buyer_41')).toBeInTheDocument();
    expect(screen.getByText('seller_41')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Simulate payment' }));

    await waitFor(() => expect(simulateSalePayment).toHaveBeenCalledWith('sale-41'));
    expect(
      await screen.findByText('Payment recorded. The seller can now record shipment.'),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/Waiting for seller_41 to record the carrier and tracking reference/),
    ).toBeInTheDocument();
  });

  test('lets the seller record carrier and tracking after payment', async () => {
    const shipmentPendingSellerSale: Sale = {
      ...paymentPendingBuyerSale,
      participantRole: 'SELLER',
      state: 'SHIPMENT_PENDING',
      paidAt: '2026-09-18T12:15:00Z',
      shipmentDeadlineAt: '2026-09-21T12:15:00Z',
    };
    vi.mocked(listMySales).mockResolvedValue([shipmentPendingSellerSale]);
    vi.mocked(recordSaleShipment).mockResolvedValue({
      ...shipmentPendingSellerSale,
      state: 'SHIPPED',
      shippedAt: '2026-09-18T12:20:00Z',
      carrier: 'Correios',
      trackingReference: 'BR123',
    });

    render(<SettlementWorkspace />);

    await screen.findByText('Signed settlement card');
    fireEvent.change(screen.getByLabelText('Carrier'), { target: { value: 'Correios' } });
    fireEvent.change(screen.getByLabelText('Tracking reference'), { target: { value: 'BR123' } });
    fireEvent.click(screen.getByRole('button', { name: 'Record shipment' }));

    await waitFor(() =>
      expect(recordSaleShipment).toHaveBeenCalledWith('sale-41', {
        carrier: 'Correios',
        trackingReference: 'BR123',
      }),
    );
    expect(
      await screen.findByText(
        'Shipment recorded. The buyer can now see the carrier and tracking reference.',
      ),
    ).toBeInTheDocument();
    expect(screen.getByText('Carrier')).toBeInTheDocument();
    expect(screen.getByText('BR123')).toBeInTheDocument();
  });
});
