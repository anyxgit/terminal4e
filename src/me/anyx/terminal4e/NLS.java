/*
 * (c) 2026 Thunisoft, Inc. All rights reserved.
 * THUNISOFT PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package me.anyx.terminal4e;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.PropertyResourceBundle;

import org.eclipse.osgi.framework.log.FrameworkLog;
import org.eclipse.osgi.framework.log.FrameworkLogEntry;

/**
 * NLS
 *
 * @version 1.0
 * @author anyx
 */
public abstract class NLS {
    private static final Object[] EMPTY_ARGS = new Object[0];
    private static final String EXTENSION = ".properties";
//    private static String[] nlSuffixes;
    private static final String PROP_WARNINGS = "osgi.nls.warnings";
    private static final String IGNORE = "ignore";
    private static final boolean ignoreWarnings = IGNORE.equals(System.getProperty(PROP_WARNINGS));
    private static FrameworkLog frameworkLog;
    static final int SEVERITY_ERROR = 4;
    static final int SEVERITY_WARNING = 2;
    static final Object ASSIGNED = new Object();

    protected NLS() {
    }// 87

    public static String bind(String message, Object binding) {
        return internalBind(message, (Object[]) null, String.valueOf(binding), (String) null);// 98
    }

    public static String bind(String message, Object binding1, Object binding2) {
        return internalBind(message, (Object[]) null, String.valueOf(binding1), String.valueOf(binding2));// 111
    }

    @SafeVarargs
    public static <T> String bind(String message, T... bindings) {
        return internalBind(message, bindings, (String) null, (String) null);// 124
    }

    public static void initializeMessages(String baseName, Class<?> clazz, Locale locale) {
        load(baseName, clazz, locale);// 163
    }// 164 170

    public static void initializeMessages(String baseName, Class<?> clazz) {
        initializeMessages(baseName, clazz, null);// 162
    }// 164 170

    private static String internalBind(String message, Object[] args, String argZero, String argOne) {
        if (message == null) {// 177
            return "No message available.";// 178
        } else {
            if (args == null || args.length == 0) {// 179
                args = EMPTY_ARGS;// 180
            }

            int length = message.length();// 182
            int bufLen = length + args.length * 5;// 184
            if (argZero != null) {// 185
                bufLen += argZero.length() - 3;// 186
            }

            if (argOne != null) {// 187
                bufLen += argOne.length() - 3;// 188
            }

            StringBuilder buffer = new StringBuilder(bufLen < 0 ? 0 : bufLen);// 189

            for (int i = 0; i < length; ++i) {// 190
                char c = message.charAt(i);// 191
                int index;
                switch (c) {// 192
                case '\'':
                    int nextIndex = i + 1;// 228
                    if (nextIndex >= length) {// 229
                        buffer.append(c);// 230
                    } else {
                        char next = message.charAt(nextIndex);// 233
                        if (next == '\'') {// 235
                            ++i;// 236
                            buffer.append(c);// 237
                        } else {
                            index = message.indexOf(39, nextIndex);// 241
                            if (index == -1) {// 243
                                buffer.append(c);// 244
                            } else {
                                buffer.append(message, nextIndex, index);// 248
                                i = index;// 249
                            }
                        }
                    }
                    break;
                case '{':
                    index = message.indexOf(125, i);// 194
                    if (index == -1) {// 196
                        buffer.append(c);// 197
                    } else {
                        ++i;// 200
                        if (i >= length) {// 201
                            buffer.append(c);// 202
                        } else {
                            int number;
                            try {
                                number = Integer.parseInt(message.substring(i, index));// 208
                            } catch (NumberFormatException var13) {// 209
                                throw new IllegalArgumentException(var13);// 210
                            }

                            if (number == 0 && argZero != null) {// 212
                                buffer.append(argZero);// 213
                            } else if (number == 1 && argOne != null) {// 214
                                buffer.append(argOne);// 215
                            } else {
                                if (number >= args.length || number < 0) {// 217
                                    buffer.append("<missing argument>");// 218
                                    i = index;// 219
                                    continue;// 220
                                }

                                buffer.append(args[number]);// 222
                            }

                            i = index;// 224
                        }
                    }
                    break;
                default:
                    buffer.append(c);// 252
                }
            }

            return buffer.toString();// 255
        }
    }

    private static String[] buildVariants(String root, Locale locale) {
        String[] nlSuffixes;
//        if (nlSuffixes == null) {// 265
            String nl = locale == null ? Locale.getDefault().toString() : locale.toString();// 267
            List<String> result = new ArrayList<>(4);// 268

            while (true) {
                result.add('_' + nl + EXTENSION);// 271
                String additional = getAdditionalSuffix(nl);// 272
                if (additional != null) {// 273
                    result.add('_' + additional + EXTENSION);// 274
                }

                int lastSeparator = nl.lastIndexOf(95);// 276
                if (lastSeparator == -1) {// 277
                    result.add(EXTENSION);// 282
                    nlSuffixes = (String[]) result.toArray(new String[result.size()]);// 283
                    break;
                }

                nl = nl.substring(0, lastSeparator);// 279
            }
//        }

        root = root.replace('.', '/');// 285
        String[] variants = new String[nlSuffixes.length];// 286

        for (int i = 0; i < variants.length; ++i) {// 287
            variants[i] = root + nlSuffixes[i];// 288
        }

        return variants;// 289
    }

    private static String getAdditionalSuffix(String nl) {
        String additional = null;// 297
        if (nl != null) {// 298
            if ("he".equals(nl)) {// 299
                additional = "iw";// 300
            } else if (nl.startsWith("he_")) {// 301
                additional = "iw_" + nl.substring(3);// 302
            }
        }

        return additional;// 306
    }

    private static void computeMissingMessages(String bundleName, Class<?> clazz, Map<Object, Object> fieldMap, Field[] fieldArray,
                                               boolean isAccessible) {
//        int MOD_EXPECTED = true;// 311
//        int MOD_MASK = true;// 312
        int numFields = fieldArray.length;// 313

        for (int i = 0; i < numFields; ++i) {// 314
            Field field = fieldArray[i];// 315
            if ((field.getModifiers() & 25) == 9 && fieldMap.get(field.getName()) != ASSIGNED) {// 316 319
                try {
                    String value = "NLS missing message: " + field.getName() + " in: " + bundleName;// 326
                    log(2, value, (Exception) null);// 327
                    if (!isAccessible) {// 328
                        field.setAccessible(true);// 329
                    }

                    field.set((Object) null, value);// 330
                } catch (Exception var11) {// 331
                    log(4, "Error setting the missing message value for: " + field.getName(), var11);// 332
                }
            }
        }

    }// 335

    static void load(String bundleName, Class<?> clazz, Locale locale) {
//        long start = System.currentTimeMillis();// 341
        Field[] fieldArray = clazz.getDeclaredFields();// 342
        ClassLoader loader = clazz.getClassLoader();// 343
        boolean isAccessible = (clazz.getModifiers() & 1) != 0;// 345
        int len = fieldArray.length;// 348
        Map<Object, Object> fields = new HashMap<>(len * 2);// 349

        for (int i = 0; i < len; ++i) {// 350
            fields.put(fieldArray[i].getName(), fieldArray[i]);// 351
        }

        String[] variants = buildVariants(bundleName, locale);// 356
        String[] var13 = variants;
        int var12 = variants.length;

        for (int var11 = 0; var11 < var12; ++var11) {// 357
            String variant = var13[var11];
            InputStream input = loader == null ? ClassLoader.getSystemResourceAsStream(variant) : loader.getResourceAsStream(variant);// 359
            if (input != null) {// 360
                try {
                    MessagesProperties properties = new MessagesProperties(fields, bundleName, isAccessible);// 363
                    PropertyResourceBundle bundle = new PropertyResourceBundle(input);// 364
                    Iterator<String> var18 = bundle.keySet().iterator();// 365

                    while (var18.hasNext()) {
                        String key = var18.next();
                        properties.put(key, bundle.getString(key));// 366
                    }
                } catch (IOException var27) {// 368
                    log(4, "Error loading " + variant, var27);// 369
                } finally {
                    if (input != null) {// 371
                        try {
                            input.close();// 373
                        } catch (IOException var26) {// 374
                        }
                    }

                }
            }
        }

        computeMissingMessages(bundleName, clazz, fields, fieldArray, isAccessible);// 379
    }// 380

    static void log(int severity, String message, Exception e) {
        if (severity != 2 || !ignoreWarnings) {// 395
            if (frameworkLog != null) {// 397
                frameworkLog.log(new FrameworkLogEntry("org.eclipse.osgi", severity, 1, message, 0, e, (FrameworkLogEntry[]) null));// 398
            } else {
                String statusMsg;
                switch (severity) {// 402
                case SEVERITY_WARNING:
                case 3:
                default:
                    statusMsg = "Warning: ";// 409
                    break;
                case SEVERITY_ERROR:
                    statusMsg = "Error: ";// 404
                }

                if (message != null) {// 411
                    statusMsg = statusMsg + message;// 412
                }

                if (e != null) {// 413
                    statusMsg = statusMsg + ": " + e.getMessage();// 414
                }

                System.err.println(statusMsg);// 415
                if (e != null) {// 416
                    e.printStackTrace();// 417
                }

            }
        }
    }// 396 399 418

    private static class MessagesProperties extends Properties {
        private static final long serialVersionUID = 1L;
        private static final int MOD_EXPECTED = 9;
        private static final int MOD_MASK = 25;
        private final String bundleName;
        private final Map<Object, Object> fields;
        private final boolean isAccessible;

        public MessagesProperties(Map<Object, Object> fieldMap, String bundleName, boolean isAccessible) {
            this.fields = fieldMap;// 436
            this.bundleName = bundleName;// 437
            this.isAccessible = isAccessible;// 438
        }// 439

        @Override
        public synchronized Object put(Object key, Object value) {
            Object fieldObject = this.fields.put(key, NLS.ASSIGNED);// 446
            if (fieldObject == NLS.ASSIGNED) {// 448
                return null;// 449
            } else if (fieldObject == null) {// 450
                String msg = "NLS unused message: " + key + " in: " + this.bundleName;// 451
                if (key instanceof String && ((String) key).indexOf(46) < 0) {// 453
                    NLS.log(2, msg, (Exception) null);// 454
                }

                return null;// 456
            } else {
                Field field = (Field) fieldObject;// 458
                if ((field.getModifiers() & MOD_MASK) != MOD_EXPECTED) {// 460
                    return null;// 461
                } else {
                    try {
                        if (!this.isAccessible) {// 466
                            field.setAccessible(true);// 467
                        }

                        field.set((Object) null, new String(((String) value).toCharArray()));// 475
                    } catch (Exception var6) {// 476
                        NLS.log(4, "Exception setting field value.", var6);// 477
                    }

                    return null;// 479
                }
            }
        }
    }
}

/*
 DECOMPILATION REPORT

 Decompiled from: D:\eclipse\plugins\org.eclipse.osgi_3.24.0.v20251126-0427.jar
 Total time: 55 ms

 Decompiled with FernFlower version 232.10203.10.
*/
