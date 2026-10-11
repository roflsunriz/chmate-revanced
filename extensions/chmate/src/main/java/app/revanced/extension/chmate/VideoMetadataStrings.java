package app.revanced.extension.chmate;

import java.util.Locale;

final class VideoMetadataStrings {
    final String views;
    final String comments;
    final String mylists;
    final String likes;
    final String publishedAt;
    final String author;

    private VideoMetadataStrings(String views, String comments, String mylists,
            String likes, String publishedAt, String author) {
        this.views = views;
        this.comments = comments;
        this.mylists = mylists;
        this.likes = likes;
        this.publishedAt = publishedAt;
        this.author = author;
    }

    static VideoMetadataStrings forLocale(Locale locale) {
        switch (locale.getLanguage()) {
            case "ja": return new VideoMetadataStrings("再生", "コメント", "マイリスト", "いいね", "投稿日", "投稿者");
            case "zh": return new VideoMetadataStrings("播放", "评论", "收藏", "点赞", "发布于", "发布者");
            case "hi": return new VideoMetadataStrings("दृश्य", "टिप्पणियाँ", "सूचियाँ", "पसंद", "प्रकाशित", "रचनाकार");
            case "es": return new VideoMetadataStrings("Vistas", "Comentarios", "Listas", "Me gusta", "Publicado", "Autor");
            case "fr": return new VideoMetadataStrings("Vues", "Commentaires", "Listes", "J’aime", "Publié", "Auteur");
            case "ar": return new VideoMetadataStrings("المشاهدات", "التعليقات", "القوائم", "الإعجابات", "نُشر", "الناشر");
            case "pt": return new VideoMetadataStrings("Visualizações", "Comentários", "Listas", "Curtidas", "Publicado", "Autor");
            case "bn": return new VideoMetadataStrings("দর্শন", "মন্তব্য", "তালিকা", "পছন্দ", "প্রকাশিত", "প্রকাশক");
            case "ru": return new VideoMetadataStrings("Просмотры", "Комментарии", "Списки", "Нравится", "Опубликовано", "Автор");
            case "ur": return new VideoMetadataStrings("مناظر", "تبصرے", "فہرستیں", "پسند", "شائع شدہ", "تخلیق کار");
            default: return new VideoMetadataStrings("Views", "Comments", "Mylists", "Likes", "Published", "Creator");
        }
    }

    static String setting(Locale locale) {
        switch (locale.getLanguage()) {
            case "ja": return "スレッド内に動画情報を表示（開き直すと反映）";
            case "zh": return "在线程中显示视频信息（重新打开后生效）";
            case "hi": return "थ्रेड में वीडियो जानकारी दिखाएँ (फिर खोलने पर लागू)";
            case "es": return "Mostrar datos del video en el hilo (volver a abrir)";
            case "fr": return "Afficher les infos vidéo dans le fil (rouvrir le fil)";
            case "ar": return "عرض معلومات الفيديو في الموضوع (أعد فتحه)";
            case "pt": return "Mostrar dados do vídeo no tópico (reabra o tópico)";
            case "bn": return "থ্রেডে ভিডিওর তথ্য দেখান (আবার খুলুন)";
            case "ru": return "Показывать данные видео в теме (откройте тему снова)";
            case "ur": return "تھریڈ میں ویڈیو معلومات دکھائیں (دوبارہ کھولیں)";
            default: return "Show video details in threads (reopen to apply)";
        }
    }
}
