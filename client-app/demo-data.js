// Offline demo data, used only when the backend (Server A / Server B / agent-worker) is unreachable,
// so the UI can still be shown. Dates are relative to "today".
(function () {
  const day = 24 * 3600 * 1000;
  const iso = (offsetDays) => new Date(Date.now() + offsetDays * day).toISOString().slice(0, 10);

  const cafes = [
    { cafeId: "CAFE-01", name: "Roastery Victoriei", address: "Piața Victoriei", lat: 45.7537, lng: 21.2257 },
    { cafeId: "CAFE-02", name: "Campus Cafe", address: "Complex Studențesc", lat: 45.7472, lng: 21.2390 },
    { cafeId: "CAFE-03", name: "Iulius Cafe", address: "Iulius Town", lat: 45.7660, lng: 21.2280 },
  ];

  const batches = {
    "CAFE-01": [
      { batchId: "B-ESP-31", name: "Ethiopia Guji espresso", category: "ESPRESSO_BEAN", remainingQuantity: 2400, roastOrOpenDate: iso(-9) },
      { batchId: "B-DEC-07", name: "Colombia decaf", category: "DECAF_BEAN", remainingQuantity: 1000, roastOrOpenDate: iso(-20) },
      { batchId: "B-MAT-04", name: "Ceremonial matcha Tin #4", category: "MATCHA_TIN", remainingQuantity: 80, roastOrOpenDate: iso(-26) },
      { batchId: "B-OAT-02", name: "Oat milk", category: "OAT_MILK", remainingQuantity: 1200, roastOrOpenDate: iso(-3), expiryDate: iso(1) },
      { batchId: "B-COC-01", name: "Belgian cocoa", category: "COCOA_BATCH", remainingQuantity: 900, roastOrOpenDate: iso(-10) },
    ],
    "CAFE-02": [
      { batchId: "B-ESP-33", name: "Brazil Cerrado espresso", category: "ESPRESSO_BEAN", remainingQuantity: 3100, roastOrOpenDate: iso(-5) },
      { batchId: "B-DEC-09", name: "Peru decaf", category: "DECAF_BEAN", remainingQuantity: 150, roastOrOpenDate: iso(-6) },
      { batchId: "B-MAT-06", name: "Ceremonial matcha Tin #6", category: "MATCHA_TIN", remainingQuantity: 120, roastOrOpenDate: iso(-4) },
      { batchId: "B-TEA-02", name: "Sencha", category: "LOOSE_TEA", remainingQuantity: 400, roastOrOpenDate: iso(-15) },
    ],
    "CAFE-03": [
      { batchId: "B-FIL-12", name: "Kenya AA filter", category: "FILTER_BEAN", remainingQuantity: 1500, roastOrOpenDate: iso(-2) },
      { batchId: "B-ESP-29", name: "House blend espresso", category: "ESPRESSO_BEAN", remainingQuantity: 800, roastOrOpenDate: iso(-17) },
      { batchId: "B-TEA-05", name: "Earl Grey", category: "LOOSE_TEA", remainingQuantity: 600, roastOrOpenDate: iso(-30) },
      { batchId: "B-COC-03", name: "Dark cocoa", category: "COCOA_BATCH", remainingQuantity: 1100, roastOrOpenDate: iso(-8) },
    ],
  };

  const menu = [
    { itemId: "M-ESPRESSO", name: "Espresso", beverageGroup: "ESPRESSO", basePriceRon: 9, activeDiscountPct: 0 },
    { itemId: "M-FLAT-WHITE", name: "Flat White", beverageGroup: "ESPRESSO", basePriceRon: 15, activeDiscountPct: 0 },
    { itemId: "M-V60", name: "V60 Filter", beverageGroup: "ESPRESSO", basePriceRon: 16, activeDiscountPct: 0 },
    { itemId: "M-DECAF-LATTE", name: "Decaf Latte", beverageGroup: "DECAF", basePriceRon: 15, activeDiscountPct: 0 },
    { itemId: "M-ICED-MATCHA", name: "Iced Matcha Latte", beverageGroup: "MATCHA", basePriceRon: 19, activeDiscountPct: 25 },
    { itemId: "M-MATCHA", name: "Ceremonial Matcha", beverageGroup: "MATCHA", basePriceRon: 17, activeDiscountPct: 0 },
    { itemId: "M-SENCHA", name: "Sencha", beverageGroup: "TEA", basePriceRon: 12, activeDiscountPct: 0 },
    { itemId: "M-HOT-CHOC", name: "Hot Chocolate", beverageGroup: "HOT_CHOCOLATE", basePriceRon: 14, activeDiscountPct: 25 },
  ];

  const now = new Date().toISOString();
  const proposals = [
    {
      proposalId: "P-DEMO-1", type: "FLASH_PROMO", status: "PENDING", cafeId: "CAFE-01",
      menuItemIds: ["M-ICED-MATCHA", "M-HOT-CHOC"], discountPct: 25, durationHours: 3,
      reason: "25% off Iced Matcha & Hot Chocolate 14:00–17:00 to clear 1.2L oat milk (B-OAT-02, expires in ~41h) and matcha Tin #4 (B-MAT-04, opened 26 days ago) before oxidation. Matcha sells only 4 cups/day at this cafe.",
      proposedBy: "ai-agent", createdAt: now,
    },
    {
      proposalId: "P-DEMO-2", type: "INTER_CAFE_TRANSFER", status: "PENDING", fromCafeId: "CAFE-01", toCafeId: "CAFE-02",
      batchId: "B-DEC-07", quantityGrams: 500,
      reason: "Move 500 g Colombia decaf (B-DEC-07, roasted 20 days ago) from Roastery Victoriei (2 decaf cups/day) to Campus Cafe (18 cups/day, accelerating, only 150 g left) 1.3 km away before it passes 21 days.",
      proposedBy: "ai-agent", createdAt: now,
    },
  ];

  window.DEMO_DATA = { cafes, batches, menu, proposals };
})();
