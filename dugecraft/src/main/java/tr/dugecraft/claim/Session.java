package tr.dugecraft.claim;

import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Oyuncu basina claim.sk menu durumu (Skript "metadata tag ... of player")
 * ve oyuncuya bagli bellek degiskenleri ({-...::<uuid>}).
 */
final class Session {

    /** Acik menu turu (metadata tag adi: "claim_menu", "farmer_menu"...) ve envanteri */
    volatile String menu;
    volatile Inventory inv;

    // metadata etiketleri
    volatile String cid;              // claim_cid
    volatile Integer logPage;         // claim_log_page
    volatile String farmerItemKey;    // farmer_item_key
    volatile String mapMode;          // map_mode
    volatile String mapBase;          // map_base_cid
    volatile String mapCenter;        // map_center_cid
    volatile String target;           // hedef oyuncu (perm / devir menuleri)
    final Map<Integer, String> slotTags = new ConcurrentHashMap<>();   // claim_slot_N, cm_act_N, f_slot_N ...

    // sohbet girdisi bekleme durumu
    volatile String chatMode;
    volatile String chatCid;
    volatile double chatAt = Double.NaN;
    volatile double chatPrice;
    volatile String chunkDeleteSrc;
    volatile boolean timerDone;
    // isinlanma varisi ({-tp_land}, {-tp_stone}, {-tp_stone_t}, {-tp_fx})
    volatile boolean tpLand;
    volatile Location tpStone;
    volatile double tpStoneT = Double.NaN;
    volatile boolean tpFx;
    // {-stone_pending::<uuid>::*}: sohbetten kurulan claim'in tasi (oyuncunun bolgesinde konur)
    volatile String stonePendingCid;
    volatile Location stonePendingLoc;
    // {-stone_scan::<uuid>}
    volatile double stoneScan = Double.NaN;

    // {-map_mark::<uuid>}
    volatile boolean mapMark;
    // {-cchat::<uuid>} claim sohbeti acik oldugu ana claim
    volatile String cchat;

    // chunk isareti
    volatile String cmarkW;
    volatile int cmarkX;
    volatile int cmarkZ;
    volatile org.bukkit.Particle.DustOptions cmarkC;
    volatile double cmarkUntil = Double.NaN;
    final List<Location> cmarkFb = new ArrayList<>();
    volatile double cmarkFbt = Double.NaN;

    // ciftci canli guncelleme ({-fview*::<uuid>})
    volatile String fview;
    volatile String fviewMode;
    volatile long fviewTok;
    volatile String fviewVer;

    // actionbar / placeholder
    volatile String abarLast;
    volatile String abar;
    volatile double abarUntil = Double.NaN;
    volatile String plcChunk;

    // yukseltme onayi (upg_key / upg_act)
    volatile String upgKey;
    volatile String upgAct;
    // {fas_dirty}: oto satis menusunde degisiklik yapilan ana claim
    volatile String fasDirty;
    // cift tiklama korumalari ({-energy_click}, {-chunkdel_click}, {-chunkbuy_click})
    volatile double energyClick = Double.NaN;
    volatile double chunkDelClick = Double.NaN;
    volatile double chunkBuyClick = Double.NaN;
    // uye davet girdisi zaman damgasi (60 sn iptal mesaji icin)
    volatile long memberInputTok;

    volatile BlockData unused;

    void clearMenu() {
        menu = null;
        inv = null;
        slotTags.clear();
    }
}
