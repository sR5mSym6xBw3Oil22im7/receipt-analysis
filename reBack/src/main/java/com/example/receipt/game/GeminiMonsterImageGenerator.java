package com.example.receipt.game;

import com.example.receipt.exception.ReceiptException;
import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.ImageConfig;
import com.google.genai.types.Part;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;

@Service
public class GeminiMonsterImageGenerator {
    private final String model;
    private final int timeoutMs;
    public GeminiMonsterImageGenerator(@Value("${gemini.image-model:gemini-3.1-flash-image}") String model, @Value("${gemini.image-timeout-ms:180000}") int timeoutMs) { this.model=model; this.timeoutMs=timeoutMs; }

    public byte[] generate(GameMonsterProfile profile, String apiKey) {
        final String key;
        try { key=com.example.receipt.service.GeminiApiKeyPolicy.requireValid(apiKey); } catch (IllegalArgumentException e) { throw new ReceiptException(HttpStatus.BAD_REQUEST,"GEMINI_API_KEY_MISSING","Gemini APIキーを入力してください。"); }
        String prompt="""
                Create one original fantasy game monster for a receipt battle game.
                Species: %s
                Rarity: %s
                Main color: %s
                Body shape: %s
                Horn: %s
                Wing: %s
                Armor: %s
                Aura: %s
                Personality: %s
                Abstract visual motifs: %s
                The motifs must be abstract creature design elements, never product packages.
                Do not draw receipt paper, store or product logos, store names, readable product names, address, phone number, barcode, QR code, payment information, member ID, card number, or readable text.
                One monster only. Square composition. Game monster card illustration.
                """.formatted(profile.species(), profile.rarity(), value(profile.visualProfileJson(), "mainColor"), value(profile.visualProfileJson(), "bodyShape"), value(profile.visualProfileJson(), "hornType"), value(profile.visualProfileJson(), "wingType"), value(profile.visualProfileJson(), "armorType"), value(profile.visualProfileJson(), "auraType"), value(profile.visualProfileJson(), "personality"), profile.visualProfileJson());
        try (Client client=Client.builder().apiKey(key).httpOptions(HttpOptions.builder().timeout(timeoutMs).build()).build()) {
            GenerateContentConfig config=GenerateContentConfig.builder().candidateCount(1).responseModalities("IMAGE").imageConfig(ImageConfig.builder().aspectRatio("1:1").outputMimeType("image/jpeg").outputCompressionQuality(90).build()).build();
            GenerateContentResponse response=client.models.generateContent(model, Content.fromParts(Part.fromText(prompt)), config);
            for (Part part: response.parts()) { if (part.inlineData().isPresent() && part.inlineData().get().data().isPresent()) return normalize(part.inlineData().get().data().get()); }
            throw new ReceiptException(HttpStatus.BAD_GATEWAY,"GEMINI_IMAGE_EMPTY","Geminiからモンスター画像を取得できませんでした。");
        } catch (ReceiptException e) { throw e; } catch (ApiException e) { throw new ReceiptException(e.code()==429?HttpStatus.TOO_MANY_REQUESTS:HttpStatus.BAD_GATEWAY,"GEMINI_IMAGE_ERROR","Geminiのモンスター画像生成に失敗しました。モデルとAPIキーを確認してください。"); } catch (Exception e) { throw new ReceiptException(HttpStatus.BAD_GATEWAY,"GEMINI_IMAGE_ERROR","Geminiのモンスター画像生成に失敗しました。"); }
    }
    private static String value(String json,String key) { String marker="\""+key+"\":\""; int start=json.indexOf(marker); if(start<0)return "UNKNOWN"; start+=marker.length(); int end=json.indexOf('\"',start); return end<0?"UNKNOWN":json.substring(start,end); }
    static byte[] normalize(byte[] bytes) throws Exception {
        BufferedImage source=ImageIO.read(new ByteArrayInputStream(bytes)); if(source==null)throw new IllegalArgumentException("invalid image");
        BufferedImage out=new BufferedImage(512,512,BufferedImage.TYPE_INT_RGB); Graphics2D g=out.createGraphics(); g.setColor(Color.WHITE); g.fillRect(0,0,512,512); double scale=Math.min(512d/source.getWidth(),512d/source.getHeight()); int w=(int)(source.getWidth()*scale),h=(int)(source.getHeight()*scale); g.drawImage(source,(512-w)/2,(512-h)/2,w,h,null); g.dispose();
        ByteArrayOutputStream outBytes=new ByteArrayOutputStream(); Iterator<ImageWriter> it=ImageIO.getImageWritersByFormatName("jpeg"); if(!it.hasNext())throw new IllegalStateException("jpeg writer unavailable"); ImageWriter writer=it.next(); var ios=ImageIO.createImageOutputStream(outBytes); writer.setOutput(ios); ImageWriteParam param=writer.getDefaultWriteParam(); param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT); param.setCompressionQuality(.88f); writer.write(null,new IIOImage(out,null,null),param); ios.close(); writer.dispose(); if(outBytes.size()>1_048_576) throw new IllegalArgumentException("image too large"); return outBytes.toByteArray();
    }
}
