package app.hushmessenger.patches.controls

/**
 * What differs between the Redex builds of Messenger the controls support. It's the same app
 * under different obfuscated names and a few shifted instruction positions, so each build keeps its
 * own exact hook list and the references the validators pin. Nothing is matched by count alone.
 *
 * scripts/profiles records each build, and `CompatReport.java --kotlin` turns a record into a new
 * profile. CompatProfileTest fails when a record and its profile here disagree.
 */
internal class ControlProfile(
    val hooks: Map<String, Set<String>>,
    /** Plugin gates compare their cached answer with this "not computed yet" sentinel. */
    val pluginSentinel: String,
    val preferenceGetter: String,
    /** The Notifications tab's "hide suggestions" preference key and its server-override check. */
    val peopleKey: String,
    val peopleFlagCheck: String,
    val subtabsSupplier: String,
    /** The external-browser preference key and the index of the read that loads it. */
    val browserPreferenceKey: String,
    val browserPreferenceIndex: Int,
    /** Instruction count and exits of the inbox item processor the ad filter edits. */
    val adFilterSize: Int,
    val adFilterExits: List<Int>,
    val bubbleCapabilityGetter: String,
    val bubbleRolloutGetter: String,
    /** Existing attachment, long-lived shortcut and conversation routes, separated by |. */
    val nativeBubbleRoutes: String,
    /** Main section closure, scope and native joined predicate, connected before any mutation. */
    val nativeCommunityInbox: String,
)

/**
 * Twelve of the 14 arm64 APKs APKMirror lists for 582.0.0.61.92 share this mapping. Generated from
 * 346415686's record.
 */
internal val BASE_PROFILE = ControlProfile(
    hooks = mapOf(
        "ad_context_banner" to setOf("LX/KEY;->A00()Z"),
        "ad_events" to setOf(
            "LX/27U;->Dgy(LX/0Co;Z)V",
            "LX/6N0;->A00(LX/6G1;Lcom/facebook/auth/usersession/FbUserSession;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
        ),
        "ads" to setOf("LX/2I2;->D5T(LX/1jp;Lcom/google/common/collect/ImmutableList;Ljava/lang/String;)Lcom/google/common/collect/ImmutableList;"),
        "ai_fab" to setOf("LX/6sQ;->render(LX/2Cg;)LX/1GU;"),
        "ai_menu" to setOf("LX/WRi;->A00()Z", "LX/WRi;->A01()Z", "LX/WWJ;->A00()Z", "LX/WWJ;->A01()Z"),
        "ai_search" to setOf("LX/5WO;->A0A(LX/5WO;)Z", "LX/5WO;->A0B(LX/5WO;)Z"),
        "ai_search_chip" to setOf("LX/DDv;->render(LX/2Cg;)LX/1GU;"),
        "ai_sticker_cell" to setOf("LX/CoQ;->render(LX/2Cg;)LX/1GU;"),
        "ai_stickers" to setOf("LX/PAu;->A03(LX/PAu;)Z", "LX/PBK;->A07(LX/PBK;)Z"),
        "ai_tab" to setOf("LX/1kA;->A03(LX/1kA;)Z"),
        "ai_toolbar" to setOf("LX/2QB;->A05()Z"),
        "allow_screenshot" to setOf(
            "LX/4w1;->A00(Landroid/view/Window;)V", "LX/A4b;->onScreenCaptured()V", "LX/Yqb;->run()V",
            "Lcom/facebook/screenshot/ScreenshotContentObserver;->onChange(ZLandroid/net/Uri;)V",
        ),
        "analytics_uploads" to setOf(
            "LX/0bU;->onStartCommand(Landroid/content/Intent;II)I",
            "LX/0bU;->onStartJob(Landroid/app/job/JobParameters;)Z", "LX/Smm;->run()V", "LX/Vjo;->run()V",
            "Lcom/facebook/analytics2/logger/GooglePlayUploadService;->onStartCommand(Landroid/content/Intent;II)I",
            "Lcom/facebook/analytics2/logger/legacy/uploader/AlarmBasedUploadService;->onStartCommand(Landroid/content/Intent;II)I",
            "Lcom/facebook/analytics2/logger/legacy/uploader/HighPriUploadRetryReceiver;->onReceive(Landroid/content/Context;Landroid/content/Intent;)V",
            "Lcom/facebook/analytics2/logger/legacy/uploader/LollipopUploadService;->onStartCommand(Landroid/content/Intent;II)I",
            "Lcom/facebook/analytics2/logger/legacy/uploader/LollipopUploadService;->onStartJob(Landroid/app/job/JobParameters;)Z",
            "Lcom/facebook/analytics2/logger/service/LollipopUploadSafeService;->onStartCommand(Landroid/content/Intent;II)I",
            "Lcom/facebook/analytics2/logger/service/LollipopUploadSafeService;->onStartJob(Landroid/app/job/JobParameters;)Z",
        ),
        "anonymous_stories" to setOf(
            "LX/KMI;->C3m(Lcom/facebook/messaging/montage/model/MontageCard;Z)V",
            "Lcom/facebook/messaging/montage/model/MontageBucketPreview;-><init>(Lcom/facebook/messaging/montage/model/MontageBucketKey;Lcom/facebook/messaging/montage/model/MontageBucketLooperLoggingItem;Lcom/facebook/messaging/montage/model/MontageCard;Lcom/facebook/user/model/UserKey;Lcom/google/common/collect/ImmutableList;Ljava/lang/Integer;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IIZZZZZ)V",
        ),
        "app_icons" to setOf(
            "LX/7i2;->A02(Lcom/facebook/auth/usersession/FbUserSession;)Z",
            "LX/7i2;->A03(Lcom/facebook/auth/usersession/FbUserSession;)Z",
        ),
        "attribution_uploads" to setOf("Lcom/facebook/attribution/LatStatusJob;->A00(Lcom/facebook/attribution/LatStatusJob;Lcom/facebook/auth/usersession/FbUserSession;)V"),
        "avatar_stickers" to setOf("LX/PAu;->A01(LX/PAu;)Z"),
        "avatar_tabs" to setOf("Lcom/facebook/messaging/msys/thread/composer/configuration/xapp/BaseXappComposerConfigurationFactory;->A6X(LX/5vA;)V"),
        "browser" to setOf("Lcom/facebook/messaging/browser/util/MessengerBrowserLauncher;->A0L(Landroid/net/Uri;Lcom/facebook/auth/usersession/FbUserSession;)Z"),
        "bubble_mode" to setOf("LX/2LW;->A01(Lcom/facebook/auth/usersession/FbUserSession;)Z"),
        "bubbles" to setOf("LX/2LW;->A00()Z"),
        "business_suggestions" to setOf("LX/7Pr;->A05(LX/7Pr;)Z", "LX/7Qp;->A04(LX/7Qp;)Z", "LX/WRl;->A02()Z"),
        "chat_animation" to setOf("Landroidx/fragment/app/Fragment;->onCreateAnimation(IZI)Landroid/view/animation/Animation;"),
        "chat_fragment" to setOf("LX/1k2;-><init>()V"),
        "chat_inbox" to setOf("LX/1iR;-><init>()V"),
        "chat_legacy" to setOf("LX/1k1;->onCreateAnimation(IZI)Landroid/view/animation/Animation;"),
        "chat_promotions" to setOf("LX/KEa;->A0E()Z", "LX/KEa;->A0F()Z"),
        "community_inbox" to setOf("LX/28C;->invoke(Ljava/lang/Object;)Ljava/lang/Object;"),
        "delta_unsent" to setOf("LX/WMT;->Bvn(I)Z"),
        "disappearing_swipe" to setOf("Lcom/facebook/messaging/threadview/overscroll/ui/OverScrollActionBehavior;->onStartNestedScroll(Landroidx/coordinatorlayout/widget/CoordinatorLayout;Landroid/view/View;Landroid/view/View;Landroid/view/View;II)Z"),
        "emoji_drawer" to setOf(
            "LX/4mB;->AM0(LX/5pP;LX/1SN;Lcom/facebook/xapp/messaging/capability/vector/Capabilities;LX/5vA;LX/5Or;)LX/1GU;",
            "LX/51i;->render(LX/5XD;LX/5vA;Lcom/facebook/xapp/messaging/capability/vector/Capabilities;)V",
            "LX/55t;->AM0(LX/5pP;LX/1SN;Lcom/facebook/xapp/messaging/capability/vector/Capabilities;LX/5vA;LX/5Or;)LX/1GU;",
            "LX/5A3;->render(LX/2Cg;)LX/1GU;",
            "LX/5Rw;->AM0(LX/5pP;LX/1SN;Lcom/facebook/xapp/messaging/capability/vector/Capabilities;LX/5vA;LX/5Or;)LX/1GU;",
            "LX/5Z5;->AM0(LX/5pP;LX/1SN;Lcom/facebook/xapp/messaging/capability/vector/Capabilities;LX/5vA;LX/5Or;)LX/1GU;",
            "LX/5mv;->render(LX/2Cg;)LX/1GU;", "LX/5rH;->Bmd()Z",
            "LX/EfL;->invoke(Ljava/lang/Object;)Ljava/lang/Object;",
        ),
        "emoji_search" to setOf("LX/7Ql;->A8h(Landroid/text/Editable;Z)V"),
        "emoji_typeface" to setOf("LX/1Nu;->A00()Landroid/graphics/Typeface;"),
        "event_prompts" to setOf("LX/KEa;->A08()Z", "LX/KEa;->A09()Z"),
        "facebook" to setOf(
            "LX/2QB;->A0D()Z", "LX/3Ft;->A00()Z", "LX/3Gs;->A02()Z", "LX/3qk;->A00()Z", "LX/Ewk;->A06()Z",
            "LX/Gfr;->A00()Z", "LX/Gfz;->A02()Z", "LX/Gg0;->A01()Z", "LX/Gg2;->A02()Z", "LX/Gg4;->A02()Z",
            "LX/Gg9;->A03()Z", "LX/GgA;->A03()Z", "LX/GgC;->A01()Z", "LX/GgD;->A01()Z", "LX/GgF;->A06()Z",
            "LX/GgG;->A06()Z", "LX/GgH;->A06()Z", "LX/MJh;->A04()Z", "LX/MJp;->A06()Z", "LX/MK8;->A06()Z",
            "LX/WRg;->A02()Z",
        ),
        "font_by_name" to setOf("LX/N6z;->A00(Landroid/content/Context;Ljava/lang/String;I)Landroid/graphics/Typeface;"),
        "font_input" to setOf(
            "LX/5Ri;->A0A(Landroid/content/res/ColorStateList;Landroid/content/res/ColorStateList;Landroid/graphics/Typeface;Landroid/graphics/drawable/Drawable;Landroid/text/TextUtils\$TruncateAt;Landroid/text/method/MovementMethod;Landroid/widget/EditText;LX/52J;Ljava/lang/CharSequence;Ljava/lang/CharSequence;Ljava/lang/Integer;Ljava/lang/String;Ljava/util/List;[Ljava/lang/String;IIIIIIIIIIIZZZZ)V",
            "LX/O4T;->A08(Landroid/content/res/ColorStateList;Landroid/content/res/ColorStateList;Landroid/graphics/Typeface;Landroid/text/Layout\$Alignment;Landroid/text/TextUtils\$TruncateAt;Landroid/widget/EditText;Landroid/widget/TextView\$OnEditorActionListener;Ljava/lang/CharSequence;Ljava/lang/CharSequence;Ljava/util/List;FFFFIIIIIIIIIIIIIIZZ)V",
            "LX/P7u;->A01(Landroid/content/res/ColorStateList;Landroid/content/res/ColorStateList;Landroid/graphics/Typeface;Landroid/graphics/drawable/Drawable;Landroid/text/TextUtils\$TruncateAt;Landroid/text/method/MovementMethod;Landroid/widget/EditText;Ljava/lang/CharSequence;Ljava/lang/CharSequence;Ljava/lang/Integer;Ljava/lang/String;Ljava/util/List;LX/5F6;[Ljava/lang/String;FFFIIIIIIIIIIIZZZZZ)V",
        ),
        "font_layout" to setOf("LX/2Te;->A0D(Landroid/graphics/Typeface;)V"),
        "font_repository" to setOf("LX/7V1;->A00(LX/7Uy;LX/7V1;LX/5oI;I)Landroid/graphics/Typeface;"),
        "font_roboto" to setOf("LX/1z1;->A00(Landroid/content/Context;Ljava/lang/Integer;)Landroid/graphics/Typeface;"),
        "friend_requests" to setOf("LX/1zA;->A09()Z", "LX/2I2;->A01()Z"),
        "growth" to setOf("LX/1zA;->A0A()Z", "LX/27r;->A0A(LX/27r;)Z"),
        "growth_notes" to setOf("Lcom/facebook/presence/note/ui/nux/controller/NotesNuxController;->A01(Landroidx/fragment/app/Fragment;LX/Fsv;Ljava/util/List;LX/5Ug;Lkotlin/jvm/functions/Function1;)Ljava/lang/Object;"),
        "growth_story_card" to setOf("Lcom/facebook/messaging/montage/viewer/MontageViewerFragment;->A0z(Lcom/facebook/messaging/montage/viewer/MontageViewerFragment;)Z"),
        "hide_read_receipts" to setOf("LX/Ak0;->run()V"),
        "inbox_promotions" to setOf("LX/265;->A0J()Z", "LX/265;->A0K()Z"),
        "keep_unsent" to setOf("LX/S5c;->A01(Landroid/content/Intent;Lcom/facebook/auth/usersession/FbUserSession;Ljava/lang/String;)V"),
        "menu_settings" to setOf(
            "LX/9wI;->A1h()V", "LX/KEC;->Ay6(LX/0MI;)Ljava/util/ArrayList;", "LX/KQo;->onClick(Landroid/view/View;)V",
            "LX/NYv;->CDC(LX/4sU;I)V", "LX/NZ3;->A0J(Ljava/util/List;)V",
        ),
        "message_log" to setOf("Lcom/facebook/messaging/notify/type/NewMessageNotification;-><init>(Lcom/facebook/messaging/accountswitch/model/MessengerAccountType;Lcom/facebook/messaging/model/messages/Message;Lcom/facebook/messaging/model/threads/ThreadSummary;LX/97O;LX/5gg;Lcom/facebook/messaging/push/flags/ServerMessageAlertFlags;Lcom/facebook/push/constants/PushProperty;Ljava/lang/Long;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;ZZZZZZZ)V"),
        "moments" to setOf("LX/WRi;->A05()Z", "LX/WWJ;->A05()Z"),
        "original_photo" to setOf(
            "Lcom/facebook/msys/mci/transcoder/DefaultMediaTranscoder;->transcodeImage(Ljava/lang/String;DDLjava/lang/String;Ljava/util/Map;)[B",
            "Lcom/facebook/msys/mci/transcoder/DefaultMediaTranscoder;->transcodeImageAsync(Ljava/lang/String;DDLjava/lang/String;Ljava/util/Map;Lcom/facebook/msys/mci/TranscodeImageCompletionCallback;)V",
        ),
        "original_video" to setOf("Lcom/facebook/msys/mci/transcoder/DefaultMediaTranscoder;->A05(Lcom/facebook/msys/mci/TranscodeVideoCompletionCallback;Lcom/facebook/msys/mci/VideoEdits;Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;)V"),
        "people" to setOf("LX/1zA;->A0C()Z", "LX/2I2;->A03()Z"),
        "people_inbox_refresh" to setOf("Lcom/facebook/messaging/msys/threadlist/plugins/core/itemsupplier/ThreadListItemSupplierImplementation;->A0B()Lcom/google/common/collect/ImmutableList;"),
        "people_jewel" to setOf("LX/QAp;->A03(LX/3nt;LX/QAp;)Z"),
        "people_list_end" to setOf("LX/1zA;->A0B()Z", "LX/2I2;->A02()Z"),
        "people_search" to setOf("LX/ElA;->DOR(LX/GUn;Ljava/lang/Object;)LX/GXn;"),
        "people_story" to setOf("Lcom/facebook/messaging/montage/viewer/MontageViewerFragment;->A0Z(Lcom/facebook/messaging/montage/viewer/MontageViewerFragment;)V"),
        "people_tab" to setOf("LX/EtE;->A01(LX/EtE;)V"),
        "read_mailbox" to setOf("LX/9yz;->A00(Ljava/lang/Long;Ljava/lang/String;Ljava/lang/String;Lkotlin/jvm/functions/Function0;Lkotlin/jvm/functions/Function0;)V"),
        "reels_badge" to setOf("LX/4DG;->A09(LX/4DG;)Z"),
        "save_stories" to setOf("LX/KQq;->onClick(Landroid/view/View;)V"),
        "screenshot_viewers" to setOf(
            "Lcom/facebook/messaging/media/ephemeralmedia/viewer/EphemeralMediaViewerFragment;->A1D(Landroid/os/Bundle;)Landroid/app/Dialog;",
            "Lcom/facebook/messaging/media/ephemeralmedia/viewer/EphemeralMediaViewerFragment;->onResume()V",
            "Lcom/facebook/messaging/quicksnap/consumption/viewer/MsgrQuicksnapViewerFragment;->onCreateView(Landroid/view/LayoutInflater;Landroid/view/ViewGroup;Landroid/os/Bundle;)Landroid/view/View;",
        ),
        "stories" to setOf("LX/1wW;->A00()Z"),
        "subtabs" to setOf("LX/2HG;->run()V"),
        "suggested_replies" to setOf("LX/7Pr;->A06(LX/7Pr;)Z", "LX/7Qp;->A05(LX/7Qp;)Z", "LX/WRl;->A03()Z"),
        "system_camera" to setOf(
            "LX/0FS;->A01(Landroid/content/Context;Landroid/net/Uri;)Landroid/content/res/AssetFileDescriptor;",
            "LX/0gi;->A00(Landroid/content/Context;Landroid/net/Uri;)Ljava/lang/Boolean;",
            "LX/5ok;->A00(Landroid/content/Context;Landroid/os/Bundle;Landroidx/fragment/app/Fragment;Lcom/facebook/auth/usersession/FbUserSession;Lcom/facebook/messaging/model/threadkey/ThreadKey;LX/HJ6;)LX/6iH;",
            "LX/5xn;->BxF(Landroid/os/Bundle;)Z",
            "LX/7Gk;->Daf(Lcom/facebook/messaging/montage/composer/model/MontageComposerFragmentParams;Lcom/facebook/messaging/send/trigger/NavigationTrigger;)V",
        ),
        "typing" to setOf("LX/Aol;->run()V"),
        "typing_mailbox" to setOf("LX/4hB;->A0K(Ljava/lang/String;Z)LX/33K;"),
        "unsent_indicator" to setOf("LX/WMT;->BYe(I)Ljava/lang/String;"),
    ),
    pluginSentinel = "LX/1fl;->A03:Ljava/lang/Object;",
    preferenceGetter = "Lcom/facebook/prefs/shared/FbSharedPreferences;->Ai0(LX/2vf;Z)Z",
    peopleKey = "LX/GTs;->A01:LX/2vg;",
    peopleFlagCheck = "LX/17Q;->A1Y(Ljava/lang/Object;J)Z",
    subtabsSupplier = "LX/2HG;->A00:Lcom/facebook/messaging/inboxsubtabs/plugins/subtabs/itemsupplier/InboxSubtabsItemSupplierImplementation;",
    browserPreferenceKey = "LX/1G2;->A1U:LX/2vf;",
    browserPreferenceIndex = 60,
    adFilterSize = 1466,
    adFilterExits = listOf(1453, 1462),
    bubbleCapabilityGetter = "LX/1iw;->A03(Lcom/facebook/auth/usersession/FbUserSession;I)Z",
    bubbleRolloutGetter = "Lcom/facebook/mobileconfig/factory/MobileConfigUnsafeContext;->Ahw(J)Z",
    nativeBubbleRoutes = "LX/8Om;->A02(Landroid/graphics/Bitmap;LX/0MX;Lcom/facebook/auth/usersession/FbUserSession;LX/8Oq;Lcom/facebook/messaging/model/threads/ThreadSummary;LX/8Li;Lcom/facebook/push/constants/PushProperty;Z)V|LX/8Oo;->A04(Landroid/content/Context;Landroid/graphics/Bitmap;Lcom/facebook/messaging/model/threadkey/ThreadKey;Ljava/lang/String;)LX/8Oq;|LX/KIl;->A04(Landroid/content/Context;Landroid/graphics/Bitmap;Lcom/facebook/auth/usersession/FbUserSession;LX/KIl;Lcom/facebook/messaging/model/threads/ThreadSummary;)Z",
    nativeCommunityInbox = "LX/1uy;->A0H(LX/1uy;LX/1wK;Ljava/lang/String;Z)V|LX/28C;-><init>(Lcom/facebook/auth/usersession/FbUserSession;LX/27W;LX/27p;LX/28A;LX/25w;LX/287;LX/4vO;LX/51R;LX/1vm;Lcom/facebook/mig/scheme/interfaces/MigColorScheme;LX/4yF;Lcom/google/common/collect/ImmutableList;Ljava/lang/String;Lkotlin/jvm/functions/Function1;Z)V|LX/1wF;->A01()LX/1vm;|LX/1uy;->A0I(LX/1uy;LX/1vm;)V|LX/2ID;->A03:Lcom/facebook/messaging/model/threads/ThreadSummary;|LX/2i9;->A03(Lcom/facebook/messaging/model/threads/ThreadSummary;)Z|Lcom/facebook/messaging/model/threadkey/ThreadKey;->A0n(Lcom/facebook/messaging/model/threadkey/ThreadKey;)Z|Lcom/facebook/messaging/model/threadkey/ThreadKey;->A1J()Z|Lcom/facebook/messaging/model/threadkey/ThreadKey;->A1W()Z|LX/1vm;->A0F:LX/1vm;|LX/28C;->\$prefixOffsetCallback:LX/4vO;|LX/1vR;->A00:LX/1uy;|LX/1uy;->A0a:LX/1wF;|LX/1wF;->A07:LX/1wH;|LX/1wH;->A01:LX/1wR;|LX/1wR;->A00:LX/1wQ;|LX/1wQ;->A03()LX/1A4;|LX/1A4;->A0K:LX/1A4;",
)

/**
 * 346415706 and 346415707 name most classes one step earlier, and the AI sticker cell and the
 * disappearing photo dialog differently. Generated from 346415706's record.
 */
internal val PROFILE_346415706 = ControlProfile(
    hooks = mapOf(
        "ad_context_banner" to setOf("LX/HEa;->A00()Z"),
        "ad_events" to setOf(
            "LX/27T;->Dgl(LX/0Co;Z)V",
            "LX/6Lz;->A00(LX/6Ez;Lcom/facebook/auth/usersession/FbUserSession;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
        ),
        "ads" to setOf("LX/2I1;->D5H(LX/1jo;Lcom/google/common/collect/ImmutableList;Ljava/lang/String;)Lcom/google/common/collect/ImmutableList;"),
        "ai_fab" to setOf("LX/6rJ;->render(LX/2Cf;)LX/1GT;"),
        "ai_menu" to setOf("LX/HEJ;->A00()Z", "LX/HEJ;->A01()Z", "LX/HPh;->A00()Z", "LX/HPh;->A01()Z"),
        "ai_search" to setOf("LX/5Uq;->A0A(LX/5Uq;)Z", "LX/5Uq;->A0B(LX/5Uq;)Z"),
        "ai_search_chip" to setOf("LX/DGV;->render(LX/2Cf;)LX/1GT;"),
        "ai_sticker_cell" to setOf("LX/EyW;->render(LX/2Cf;)LX/1GT;"),
        "ai_stickers" to setOf("LX/Uzq;->A03(LX/Uzq;)Z", "LX/V0Y;->A07(LX/V0Y;)Z"),
        "ai_tab" to setOf("LX/1k9;->A03(LX/1k9;)Z"),
        "ai_toolbar" to setOf("LX/2QA;->A05()Z"),
        "allow_screenshot" to setOf(
            "LX/4uT;->A00(Landroid/view/Window;)V", "LX/A3I;->onScreenCaptured()V", "LX/Vk2;->run()V",
            "Lcom/facebook/screenshot/ScreenshotContentObserver;->onChange(ZLandroid/net/Uri;)V",
        ),
        "analytics_uploads" to setOf(
            "LX/0bU;->onStartCommand(Landroid/content/Intent;II)I",
            "LX/0bU;->onStartJob(Landroid/app/job/JobParameters;)Z", "LX/YjU;->run()V",
            "Lcom/facebook/analytics2/logger/GooglePlayUploadService;->A04(LX/RVX;)I",
            "Lcom/facebook/analytics2/logger/GooglePlayUploadService;->onStartCommand(Landroid/content/Intent;II)I",
            "Lcom/facebook/analytics2/logger/legacy/uploader/AlarmBasedUploadService;->onStartCommand(Landroid/content/Intent;II)I",
            "Lcom/facebook/analytics2/logger/legacy/uploader/HighPriUploadRetryReceiver;->onReceive(Landroid/content/Context;Landroid/content/Intent;)V",
            "Lcom/facebook/analytics2/logger/legacy/uploader/LollipopUploadService;->onStartCommand(Landroid/content/Intent;II)I",
            "Lcom/facebook/analytics2/logger/legacy/uploader/LollipopUploadService;->onStartJob(Landroid/app/job/JobParameters;)Z",
            "Lcom/facebook/analytics2/logger/service/LollipopUploadSafeService;->onStartCommand(Landroid/content/Intent;II)I",
            "Lcom/facebook/analytics2/logger/service/LollipopUploadSafeService;->onStartJob(Landroid/app/job/JobParameters;)Z",
        ),
        "anonymous_stories" to setOf(
            "LX/Ncc;->C3i(Lcom/facebook/messaging/montage/model/MontageCard;Z)V",
            "Lcom/facebook/messaging/montage/model/MontageBucketPreview;-><init>(Lcom/facebook/messaging/montage/model/MontageBucketKey;Lcom/facebook/messaging/montage/model/MontageBucketLooperLoggingItem;Lcom/facebook/messaging/montage/model/MontageCard;Lcom/facebook/user/model/UserKey;Lcom/google/common/collect/ImmutableList;Ljava/lang/Integer;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IIZZZZZ)V",
        ),
        "app_icons" to setOf(
            "LX/7gw;->A02(Lcom/facebook/auth/usersession/FbUserSession;)Z",
            "LX/7gw;->A03(Lcom/facebook/auth/usersession/FbUserSession;)Z",
        ),
        "attribution_uploads" to setOf("Lcom/facebook/attribution/LatStatusJob;->A00(Lcom/facebook/attribution/LatStatusJob;Lcom/facebook/auth/usersession/FbUserSession;)V"),
        "avatar_stickers" to setOf("LX/Uzq;->A01(LX/Uzq;)Z"),
        "avatar_tabs" to setOf("Lcom/facebook/messaging/msys/thread/composer/configuration/xapp/BaseXappComposerConfigurationFactory;->A6X(LX/5td;)V"),
        "browser" to setOf("Lcom/facebook/messaging/browser/util/MessengerBrowserLauncher;->A0L(Landroid/net/Uri;Lcom/facebook/auth/usersession/FbUserSession;)Z"),
        "bubble_mode" to setOf("LX/2LV;->A01(Lcom/facebook/auth/usersession/FbUserSession;)Z"),
        "bubbles" to setOf("LX/2LV;->A00()Z"),
        "business_suggestions" to setOf("LX/7Ok;->A05(LX/7Ok;)Z", "LX/7Pi;->A04(LX/7Pi;)Z", "LX/HEd;->A02()Z"),
        "chat_animation" to setOf("Landroidx/fragment/app/Fragment;->onCreateAnimation(IZI)Landroid/view/animation/Animation;"),
        "chat_fragment" to setOf("LX/1k1;-><init>()V"),
        "chat_inbox" to setOf("LX/1iQ;-><init>()V"),
        "chat_legacy" to setOf("LX/1k0;->onCreateAnimation(IZI)Landroid/view/animation/Animation;"),
        "chat_promotions" to setOf("LX/HEZ;->A0E()Z", "LX/HEZ;->A0F()Z"),
        "community_inbox" to setOf("LX/28B;->invoke(Ljava/lang/Object;)Ljava/lang/Object;"),
        "delta_unsent" to setOf("LX/WFd;->Bvj(I)Z"),
        "disappearing_swipe" to setOf("Lcom/facebook/messaging/threadview/overscroll/ui/OverScrollActionBehavior;->onStartNestedScroll(Landroidx/coordinatorlayout/widget/CoordinatorLayout;Landroid/view/View;Landroid/view/View;Landroid/view/View;II)Z"),
        "emoji_drawer" to setOf(
            "LX/4kd;->ALz(LX/5nr;LX/1SM;Lcom/facebook/xapp/messaging/capability/vector/Capabilities;LX/5td;LX/5NJ;)LX/1GT;",
            "LX/50A;->render(LX/5Vf;LX/5td;Lcom/facebook/xapp/messaging/capability/vector/Capabilities;)V",
            "LX/54L;->ALz(LX/5nr;LX/1SM;Lcom/facebook/xapp/messaging/capability/vector/Capabilities;LX/5td;LX/5NJ;)LX/1GT;",
            "LX/58V;->render(LX/2Cf;)LX/1GT;",
            "LX/5QO;->ALz(LX/5nr;LX/1SM;Lcom/facebook/xapp/messaging/capability/vector/Capabilities;LX/5td;LX/5NJ;)LX/1GT;",
            "LX/5XX;->ALz(LX/5nr;LX/1SM;Lcom/facebook/xapp/messaging/capability/vector/Capabilities;LX/5td;LX/5NJ;)LX/1GT;",
            "LX/5lN;->render(LX/2Cf;)LX/1GT;", "LX/5pj;->BmX()Z",
            "LX/H4k;->invoke(Ljava/lang/Object;)Ljava/lang/Object;",
        ),
        "emoji_search" to setOf("LX/7Pe;->A8h(Landroid/text/Editable;Z)V"),
        "emoji_typeface" to setOf("LX/1Nt;->A00()Landroid/graphics/Typeface;"),
        "event_prompts" to setOf("LX/HEZ;->A08()Z", "LX/HEZ;->A09()Z"),
        "facebook" to setOf(
            "LX/2QA;->A0D()Z", "LX/3FJ;->A00()Z", "LX/3pE;->A00()Z", "LX/HEI;->A02()Z", "LX/HLa;->A06()Z",
            "LX/HVV;->A02()Z", "LX/JNi;->A04()Z", "LX/JO2;->A06()Z", "LX/JOQ;->A06()Z", "LX/JOi;->A00()Z",
            "LX/JOq;->A02()Z", "LX/JOr;->A01()Z", "LX/JOt;->A02()Z", "LX/JOv;->A02()Z", "LX/JP0;->A03()Z",
            "LX/JP1;->A03()Z", "LX/JP3;->A01()Z", "LX/JP4;->A01()Z", "LX/JP6;->A06()Z", "LX/JP7;->A06()Z",
            "LX/JP8;->A06()Z",
        ),
        "font_by_name" to setOf("LX/K3X;->A00(Landroid/content/Context;Ljava/lang/String;I)Landroid/graphics/Typeface;"),
        "font_input" to setOf(
            "LX/5QA;->A08(Landroid/content/res/ColorStateList;Landroid/content/res/ColorStateList;Landroid/graphics/Typeface;Landroid/graphics/drawable/Drawable;Landroid/text/TextUtils\$TruncateAt;Landroid/text/method/MovementMethod;Landroid/widget/EditText;LX/50l;Ljava/lang/CharSequence;Ljava/lang/CharSequence;Ljava/lang/Integer;Ljava/lang/String;Ljava/util/List;[Ljava/lang/String;IIIIIIIIIIIZZZZ)V",
            "LX/LDe;->A06(Landroid/content/res/ColorStateList;Landroid/content/res/ColorStateList;Landroid/graphics/Typeface;Landroid/text/Layout\$Alignment;Landroid/text/TextUtils\$TruncateAt;Landroid/widget/EditText;Landroid/widget/TextView\$OnEditorActionListener;Ljava/lang/CharSequence;Ljava/lang/CharSequence;Ljava/util/List;FFFFIIIIIIIIIIIIIIZZ)V",
            "LX/P5q;->A01(Landroid/content/res/ColorStateList;Landroid/content/res/ColorStateList;Landroid/graphics/Typeface;Landroid/graphics/drawable/Drawable;Landroid/text/TextUtils\$TruncateAt;Landroid/text/method/MovementMethod;Landroid/widget/EditText;Ljava/lang/CharSequence;Ljava/lang/CharSequence;Ljava/lang/Integer;Ljava/lang/String;Ljava/util/List;LX/5DY;[Ljava/lang/String;FFFIIIIIIIIIIIZZZZZ)V",
        ),
        "font_layout" to setOf("LX/2Td;->A0D(Landroid/graphics/Typeface;)V"),
        "font_repository" to setOf("LX/7Tu;->A00(LX/7Tr;LX/7Tu;LX/5mk;I)Landroid/graphics/Typeface;"),
        "font_roboto" to setOf("LX/1z0;->A00(Landroid/content/Context;Ljava/lang/Integer;)Landroid/graphics/Typeface;"),
        "friend_requests" to setOf("LX/1z9;->A09()Z", "LX/2I1;->A01()Z"),
        "growth" to setOf("LX/1z9;->A0A()Z", "LX/27q;->A0A(LX/27q;)Z"),
        "growth_notes" to setOf("Lcom/facebook/presence/note/ui/nux/controller/NotesNuxController;->A01(Landroidx/fragment/app/Fragment;LX/OQO;Ljava/util/List;LX/5T8;Lkotlin/jvm/functions/Function1;)Ljava/lang/Object;"),
        "growth_story_card" to setOf("Lcom/facebook/messaging/montage/viewer/MontageViewerFragment;->A11(Lcom/facebook/messaging/montage/viewer/MontageViewerFragment;)Z"),
        "hide_read_receipts" to setOf("LX/Aih;->run()V"),
        "inbox_promotions" to setOf("LX/264;->A0J()Z", "LX/264;->A0K()Z"),
        "keep_unsent" to setOf("LX/MDj;->A01(Landroid/content/Intent;Lcom/facebook/auth/usersession/FbUserSession;Ljava/lang/String;)V"),
        "menu_settings" to setOf(
            "LX/9uz;->A1h()V", "LX/HEG;->Ay5(LX/0MI;)Ljava/util/ArrayList;", "LX/HUf;->onClick(Landroid/view/View;)V",
            "LX/TUj;->CD7(LX/4qw;I)V", "LX/TUm;->A0I(Ljava/util/List;)V",
        ),
        "message_log" to setOf("Lcom/facebook/messaging/notify/type/NewMessageNotification;-><init>(Lcom/facebook/messaging/accountswitch/model/MessengerAccountType;Lcom/facebook/messaging/model/messages/Message;Lcom/facebook/messaging/model/threads/ThreadSummary;LX/96C;LX/5f8;Lcom/facebook/messaging/push/flags/ServerMessageAlertFlags;Lcom/facebook/push/constants/PushProperty;Ljava/lang/Long;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;ZZZZZZZ)V"),
        "moments" to setOf("LX/HEJ;->A05()Z", "LX/HPh;->A05()Z"),
        "original_photo" to setOf(
            "Lcom/facebook/msys/mci/transcoder/DefaultMediaTranscoder;->transcodeImage(Ljava/lang/String;DDLjava/lang/String;Ljava/util/Map;)[B",
            "Lcom/facebook/msys/mci/transcoder/DefaultMediaTranscoder;->transcodeImageAsync(Ljava/lang/String;DDLjava/lang/String;Ljava/util/Map;Lcom/facebook/msys/mci/TranscodeImageCompletionCallback;)V",
        ),
        "original_video" to setOf("Lcom/facebook/msys/mci/transcoder/DefaultMediaTranscoder;->A05(Lcom/facebook/msys/mci/TranscodeVideoCompletionCallback;Lcom/facebook/msys/mci/VideoEdits;Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;)V"),
        "people" to setOf("LX/1z9;->A0C()Z", "LX/2I1;->A03()Z"),
        "people_inbox_refresh" to setOf("Lcom/facebook/messaging/msys/threadlist/plugins/core/itemsupplier/ThreadListItemSupplierImplementation;->A0B()Lcom/google/common/collect/ImmutableList;"),
        "people_jewel" to setOf("LX/Q5m;->A04(LX/3mK;LX/Q5m;)Z"),
        "people_list_end" to setOf("LX/1z9;->A0B()Z", "LX/2I1;->A02()Z"),
        "people_search" to setOf("LX/ChB;->DOF(LX/EFG;Ljava/lang/Object;)LX/EHA;"),
        "people_story" to setOf("Lcom/facebook/messaging/montage/viewer/MontageViewerFragment;->A0b(Lcom/facebook/messaging/montage/viewer/MontageViewerFragment;)V"),
        "people_tab" to setOf("LX/HQU;->A01(LX/HQU;)V"),
        "read_mailbox" to setOf("LX/9xg;->A00(Ljava/lang/Long;Ljava/lang/String;Ljava/lang/String;Lkotlin/jvm/functions/Function0;Lkotlin/jvm/functions/Function0;)V"),
        "reels_badge" to setOf("LX/4Bi;->A09(LX/4Bi;)Z"),
        "save_stories" to setOf("LX/NCw;->onClick(Landroid/view/View;)V"),
        "screenshot_viewers" to setOf(
            "Lcom/facebook/messaging/media/ephemeralmedia/viewer/EphemeralMediaViewerFragment;->A1F(Landroid/os/Bundle;)Landroid/app/Dialog;",
            "Lcom/facebook/messaging/media/ephemeralmedia/viewer/EphemeralMediaViewerFragment;->onResume()V",
            "Lcom/facebook/messaging/quicksnap/consumption/viewer/MsgrQuicksnapViewerFragment;->onCreateView(Landroid/view/LayoutInflater;Landroid/view/ViewGroup;Landroid/os/Bundle;)Landroid/view/View;",
        ),
        "stories" to setOf("LX/1wV;->A00()Z"),
        "subtabs" to setOf("LX/2HF;->run()V"),
        "suggested_replies" to setOf("LX/7Ok;->A06(LX/7Ok;)Z", "LX/7Pi;->A05(LX/7Pi;)Z", "LX/HEd;->A03()Z"),
        "system_camera" to setOf(
            "LX/0FS;->A01(Landroid/content/Context;Landroid/net/Uri;)Landroid/content/res/AssetFileDescriptor;",
            "LX/0gi;->A00(Landroid/content/Context;Landroid/net/Uri;)Ljava/lang/Boolean;",
            "LX/5nC;->A00(Landroid/content/Context;Landroid/os/Bundle;Landroidx/fragment/app/Fragment;Lcom/facebook/auth/usersession/FbUserSession;Lcom/facebook/messaging/model/threadkey/ThreadKey;LX/Eq1;)LX/6h6;",
            "LX/5wE;->BxB(Landroid/os/Bundle;)Z",
            "LX/7Fd;->DaT(Lcom/facebook/messaging/montage/composer/model/MontageComposerFragmentParams;Lcom/facebook/messaging/send/trigger/NavigationTrigger;)V",
        ),
        "typing" to setOf("LX/AnS;->run()V"),
        "typing_mailbox" to setOf("LX/4fd;->A0J(Ljava/lang/String;Z)LX/33J;"),
        "unsent_indicator" to setOf("LX/WFd;->BYY(I)Ljava/lang/String;"),
    ),
    pluginSentinel = "LX/1fk;->A03:Ljava/lang/Object;",
    preferenceGetter = "Lcom/facebook/prefs/shared/FbSharedPreferences;->Ahz(LX/2ve;Z)Z",
    peopleKey = "LX/J6T;->A01:LX/2vf;",
    peopleFlagCheck = "LX/17P;->A1Y(Ljava/lang/Object;J)Z",
    subtabsSupplier = "LX/2HF;->A00:Lcom/facebook/messaging/inboxsubtabs/plugins/subtabs/itemsupplier/InboxSubtabsItemSupplierImplementation;",
    browserPreferenceKey = "LX/1G1;->A1U:LX/2ve;",
    browserPreferenceIndex = 60,
    adFilterSize = 1466,
    adFilterExits = listOf(1453, 1462),
    bubbleCapabilityGetter = "LX/1iv;->A03(Lcom/facebook/auth/usersession/FbUserSession;I)Z",
    bubbleRolloutGetter = "Lcom/facebook/mobileconfig/factory/MobileConfigUnsafeContext;->Ahv(J)Z",
    nativeBubbleRoutes = "LX/8Nf;->A02(Landroid/graphics/Bitmap;LX/0MX;Lcom/facebook/auth/usersession/FbUserSession;LX/8Nj;Lcom/facebook/messaging/model/threads/ThreadSummary;LX/8Kb;Lcom/facebook/push/constants/PushProperty;Z)V|LX/8Nh;->A04(Landroid/content/Context;Landroid/graphics/Bitmap;Lcom/facebook/messaging/model/threadkey/ThreadKey;Ljava/lang/String;)LX/8Nj;|LX/Q8v;->A04(Landroid/content/Context;Landroid/graphics/Bitmap;Lcom/facebook/auth/usersession/FbUserSession;LX/Q8v;Lcom/facebook/messaging/model/threads/ThreadSummary;)Z",
    nativeCommunityInbox = "LX/1ux;->A0H(LX/1ux;LX/1wJ;Ljava/lang/String;Z)V|LX/28B;-><init>(Lcom/facebook/auth/usersession/FbUserSession;LX/27V;LX/27o;LX/289;LX/25v;LX/286;LX/4tq;LX/4zt;LX/1vl;Lcom/facebook/mig/scheme/interfaces/MigColorScheme;LX/4wh;Lcom/google/common/collect/ImmutableList;Ljava/lang/String;Lkotlin/jvm/functions/Function1;Z)V|LX/1wE;->A01()LX/1vl;|LX/1ux;->A0I(LX/1ux;LX/1vl;)V|LX/2IC;->A03:Lcom/facebook/messaging/model/threads/ThreadSummary;|LX/2i8;->A03(Lcom/facebook/messaging/model/threads/ThreadSummary;)Z|Lcom/facebook/messaging/model/threadkey/ThreadKey;->A0n(Lcom/facebook/messaging/model/threadkey/ThreadKey;)Z|Lcom/facebook/messaging/model/threadkey/ThreadKey;->A1J()Z|Lcom/facebook/messaging/model/threadkey/ThreadKey;->A1W()Z|LX/1vl;->A0F:LX/1vl;|LX/28B;->\$prefixOffsetCallback:LX/4tq;|LX/1vQ;->A00:LX/1ux;|LX/1ux;->A0a:LX/1wE;|LX/1wE;->A07:LX/1wG;|LX/1wG;->A01:LX/1wQ;|LX/1wQ;->A00:LX/1wP;|LX/1wP;->A03()LX/1A3;|LX/1A3;->A0K:LX/1A3;",
)

/** Each supported build's profile, by version code. Builds that share a mapping share a profile. */
internal val controlProfiles: Map<Int, ControlProfile> = mapOf(
    346415686 to BASE_PROFILE,
    346415687 to BASE_PROFILE,
    346415690 to BASE_PROFILE,
    346415706 to PROFILE_346415706,
    346415707 to PROFILE_346415706,
    346415720 to BASE_PROFILE,
    346415721 to BASE_PROFILE,
    346415723 to BASE_PROFILE,
    346415759 to BASE_PROFILE,
    346415772 to BASE_PROFILE,
    346415773 to BASE_PROFILE,
    346415774 to BASE_PROFILE,
    346415776 to BASE_PROFILE,
    346415777 to BASE_PROFILE,
)

/** The table [controlProfileFor] reads. Tests that run the patch on a synthetic APK swap it. */
internal var controlProfilesInUse: Map<Int, ControlProfile> = controlProfiles

/** An unknown build gets the base profile, whose exact hooks then refuse it. */
internal fun controlProfileFor(versionCode: String?, profiles: Map<Int, ControlProfile> = controlProfilesInUse): ControlProfile =
    versionCode?.toIntOrNull()?.let(profiles::get) ?: BASE_PROFILE

/** The profile of the APK being patched. The settings extension sets it before any control runs. */
internal var activeProfile: ControlProfile = BASE_PROFILE
