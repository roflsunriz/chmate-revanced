package app.revanced.extension.chmate;

import java.util.Locale;

final class IntegrationStrings {
    final String revancedSettings;
    final String advancedNg;
    final String imageBoardSources;
    final String sourceDescription;
    final String sourceHint;
    final String save;
    final String saved;
    final String invalidSource;
    final String noBbsMenuSlot;

    private IntegrationStrings(String revancedSettings, String advancedNg,
            String imageBoardSources, String sourceDescription, String sourceHint,
            String save, String saved, String invalidSource, String noBbsMenuSlot) {
        this.revancedSettings = revancedSettings;
        this.advancedNg = advancedNg;
        this.imageBoardSources = imageBoardSources;
        this.sourceDescription = sourceDescription;
        this.sourceHint = sourceHint;
        this.save = save;
        this.saved = saved;
        this.invalidSource = invalidSource;
        this.noBbsMenuSlot = noBbsMenuSlot;
    }

    static IntegrationStrings current() {
        String language = Locale.getDefault().getLanguage();
        switch (language) {
            case "ja": return of("ChMate ReVanced", "高度なNGルール", "画像掲示板の板",
                    "4chan・ふたば・jschanの板URLを1行ずつ入力します。保存後はChMateの板一覧更新で追加されます。",
                    "https://boards.4chan.org/g/\nhttps://may.2chan.net/b/\nhttps://zzzchan.xyz/tech/",
                    "保存", "保存しました", "対応するHTTPS板URLではありません: ",
                    "BBSMENU URLが5件すべて使用中です。空きを1件作ってください。");
            case "zh": return of("ChMate ReVanced 设置", "高级屏蔽规则", "图片板块",
                    "每行输入一个4chan、Futaba或jschan板块网址。保存后在ChMate中更新板块列表。",
                    "每行一个 HTTPS URL", "保存", "已保存", "不支持的板块URL: ", "请先空出一个BBSMENU URL栏位。");
            case "hi": return of("ChMate ReVanced सेटिंग", "उन्नत NG नियम", "इमेजबोर्ड बोर्ड",
                    "हर पंक्ति में 4chan, Futaba या jschan बोर्ड URL डालें। फिर ChMate की बोर्ड सूची अपडेट करें।",
                    "हर पंक्ति में एक HTTPS URL", "सहेजें", "सहेजा गया", "असमर्थित बोर्ड URL: ", "एक BBSMENU URL स्लॉट खाली करें।");
            case "es": return of("Ajustes de ChMate ReVanced", "Reglas NG avanzadas", "Tableros de imágenes",
                    "Introduce una URL de tablero de 4chan, Futaba o jschan por línea y actualiza la lista de ChMate.",
                    "Una URL HTTPS por línea", "Guardar", "Guardado", "URL de tablero no compatible: ", "Libera una ranura de URL BBSMENU.");
            case "fr": return of("Paramètres ChMate ReVanced", "Règles NG avancées", "Forums d’images",
                    "Saisissez une URL de forum 4chan, Futaba ou jschan par ligne, puis actualisez la liste ChMate.",
                    "Une URL HTTPS par ligne", "Enregistrer", "Enregistré", "URL non prise en charge : ", "Libérez un emplacement d’URL BBSMENU.");
            case "ar": return of("إعدادات ChMate ReVanced", "قواعد حجب متقدمة", "لوحات الصور",
                    "أدخل رابط لوحة 4chan أو Futaba أو jschan في كل سطر، ثم حدّث قائمة اللوحات في ChMate.",
                    "رابط HTTPS واحد في كل سطر", "حفظ", "تم الحفظ", "رابط لوحة غير مدعوم: ", "أفرغ خانة واحدة من روابط BBSMENU.");
            case "pt": return of("Configurações do ChMate ReVanced", "Regras NG avançadas", "Fóruns de imagens",
                    "Digite uma URL de 4chan, Futaba ou jschan por linha e atualize a lista de fóruns do ChMate.",
                    "Uma URL HTTPS por linha", "Salvar", "Salvo", "URL não compatível: ", "Libere um campo de URL BBSMENU.");
            case "bn": return of("ChMate ReVanced সেটিংস", "উন্নত NG নিয়ম", "ইমেজবোর্ড বোর্ড",
                    "প্রতি লাইনে একটি 4chan, Futaba বা jschan বোর্ড URL লিখুন, তারপর ChMate বোর্ড তালিকা হালনাগাদ করুন।",
                    "প্রতি লাইনে একটি HTTPS URL", "সংরক্ষণ", "সংরক্ষিত", "অসমর্থিত বোর্ড URL: ", "একটি BBSMENU URL ঘর খালি করুন।");
            case "ru": return of("Настройки ChMate ReVanced", "Расширенные правила NG", "Имиджборды",
                    "Введите по одному URL доски 4chan, Futaba или jschan в строке, затем обновите список досок ChMate.",
                    "Один HTTPS URL в строке", "Сохранить", "Сохранено", "Неподдерживаемый URL: ", "Освободите одно поле URL BBSMENU.");
            case "ur": return of("ChMate ReVanced ترتیبات", "اعلیٰ NG قواعد", "امیج بورڈز",
                    "ہر سطر میں 4chan، Futaba یا jschan بورڈ URL درج کریں، پھر ChMate کی بورڈ فہرست اپ ڈیٹ کریں۔",
                    "ہر سطر میں ایک HTTPS URL", "محفوظ کریں", "محفوظ ہوگیا", "غیر معاون بورڈ URL: ", "ایک BBSMENU URL خانہ خالی کریں۔");
            default: return of("ChMate ReVanced", "Advanced NG rules", "Imageboard boards",
                    "Enter one 4chan, Futaba, or jschan board URL per line, then refresh ChMate's board list.",
                    "One HTTPS URL per line", "Save", "Saved", "Unsupported board URL: ", "Free one BBSMENU URL slot first.");
        }
    }

    private static IntegrationStrings of(String a, String b, String c, String d, String e,
            String f, String g, String h, String i) {
        return new IntegrationStrings(a, b, c, d, e, f, g, h, i);
    }
}
