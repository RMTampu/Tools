package com.toolbox.tools.product;

import com.toolbox.tools.core.StableId;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.Currency;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class LocalizationManager {
    public static final String BAHASA_DEFAULT="id";
    private final Map<String,Map<String,String>> values=new LinkedHashMap<>();
    private final Map<String,Map<String,String[]>> plurals=new LinkedHashMap<>();

    public synchronized void put(String stringId,String localeTag,String value){
        String id=StableId.require(stringId,"stringId"),locale=requireLocale(localeTag);
        String text=Objects.requireNonNull(value,"value").trim();if(text.isEmpty())throw new IllegalArgumentException("teks kosong");
        values.computeIfAbsent(id,k->new LinkedHashMap<>()).put(locale,text);
    }

    public synchronized void putPlural(String stringId,String localeTag,String singular,String plural){
        String id=StableId.require(stringId,"stringId"),locale=requireLocale(localeTag);
        if(singular==null||singular.trim().isEmpty()||plural==null||plural.trim().isEmpty())throw new IllegalArgumentException("plural invalid");
        plurals.computeIfAbsent(id,k->new LinkedHashMap<>()).put(locale,new String[]{singular,plural});
    }

    public synchronized String resolve(String stringId,String localeTag){
        String id=StableId.require(stringId,"stringId");Map<String,String> variants=values.get(id);if(variants==null)return id;
        String locale=requireLocale(localeTag);String direct=variants.get(locale);if(direct!=null)return direct;
        String language=locale.split("-")[0];direct=variants.get(language);if(direct!=null)return direct;
        String indonesia=variants.get(BAHASA_DEFAULT);return indonesia!=null?indonesia:variants.values().iterator().next();
    }

    public synchronized String resolvePlural(String stringId,String localeTag,long count){
        String id=StableId.require(stringId,"stringId"),locale=requireLocale(localeTag);
        Map<String,String[]> variants=plurals.get(id);if(variants==null)return id;
        String[] pair=variants.get(locale);if(pair==null)pair=variants.get(locale.split("-")[0]);if(pair==null)pair=variants.get(BAHASA_DEFAULT);if(pair==null)pair=variants.values().iterator().next();
        return count+" "+(count==1?pair[0]:pair[1]);
    }

    public String formatNumber(double value,String localeTag){return NumberFormat.getNumberInstance(locale(localeTag)).format(value);}
    public String formatCurrency(double value,String currency,String localeTag){NumberFormat f=NumberFormat.getCurrencyInstance(locale(localeTag));f.setCurrency(Currency.getInstance(currency));return f.format(value);}
    public String formatDate(long millis,String localeTag){return DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT,locale(localeTag)).format(new Date(millis));}
    public boolean isRtl(String localeTag){String l=requireLocale(localeTag).split("-")[0];return "ar".equals(l)||"fa".equals(l)||"he".equals(l)||"ur".equals(l);}

    public synchronized Map<String,String> indonesia(){LinkedHashMap<String,String> out=new LinkedHashMap<>();for(Map.Entry<String,Map<String,String>> e:values.entrySet()){String v=e.getValue().get(BAHASA_DEFAULT);if(v!=null)out.put(e.getKey(),v);}return Collections.unmodifiableMap(out);}
    private static Locale locale(String tag){return Locale.forLanguageTag(requireLocale(tag));}
    private static String requireLocale(String value){Objects.requireNonNull(value,"localeTag");String n=value.trim().toLowerCase(Locale.ROOT);if(!n.matches("[a-z]{2,3}(-[a-z0-9]{2,8})*"))throw new IllegalArgumentException("locale tidak valid");return n;}
}
