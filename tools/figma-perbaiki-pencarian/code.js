// Memperbaiki kolom pencarian yang kebesaran di file Figma "ARN Kasir - Desain UI".
// Penyebab: elemen pengisi ruang bernama "Spacer" di dalam baris horizontal setinggi 100 px.
// Skrip ini mengecilkan tingginya menjadi 1 px di semua layar pada halaman "Layar".
const page = figma.root.children.find((p) => p.name === 'Layar');
if (!page) {
  figma.closePlugin('Halaman "Layar" tidak ditemukan.');
} else {
  figma.setCurrentPageAsync(page).then(() => {
    let count = 0;
    for (const sec of page.children.filter((c) => c.type === 'SECTION')) {
      for (const sp of sec.findAll((n) => n.type === 'FRAME' && n.name === 'Spacer' && n.parent && n.parent.layoutMode === 'HORIZONTAL')) {
        sp.layoutSizingVertical = 'FIXED';
        sp.resize(sp.width, 1);
        count++;
      }
    }
    figma.closePlugin('Selesai: ' + count + ' pengisi ruang diperbaiki.');
  });
}
