package com.maxieventos.contratos.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.maxieventos.contratos.entity.Contrato;
import com.maxieventos.contratos.entity.ContratoAdicional;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

/**
 * PDF del contrato y del anexo. El diseño (cabecera de marca, secciones, valor destacado, firmas y pie con página) está en
 * este código; los textos legales se leen de {@code templates/} (UTF-8) para poder cambiarlos sin tocar el diseño.
 * Fechas e importes en formato colombiano (es-CO). Los datos del contrato salen de su foto; los contratos sin foto
 * (anteriores a la Fase 4) muestran "No registrado".
 */
@Service
public class PdfGeneratorService {
    private static final Locale ES_CO = Locale.forLanguageTag("es-CO");
    private static final String SIN_DATO = "No registrado";
    private static final DateTimeFormatter FECHA_LARGA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", ES_CO);
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, HH:mm", ES_CO);
    private static final Map<String, String> TIPOS = Map.of("QUINCE_ANOS", "Quince años", "BODA", "Boda");
    private static final Map<String, String> ESTADOS = Map.of("PENDIENTE", "Pendiente de firma", "FIRMADO", "Firmado");

    // Paleta de la marca (la misma de la interfaz): ciruela y buganvilia
    private static final Color CIRUELA = new Color(42, 21, 53);
    private static final Color BUGANVILIA = new Color(179, 32, 95);
    private static final Color LILA = new Color(241, 234, 246);
    private static final Color GRIS = new Color(92, 82, 102);
    private static final Color LINEA = new Color(224, 217, 230);
    private static final Color TEXTO = new Color(35, 27, 43);
    private static final Color AMBAR_FONDO = new Color(255, 241, 208);
    private static final Color AMBAR_TEXTO = new Color(122, 77, 0);
    private static final Color VERDE_FONDO = new Color(227, 243, 239);
    private static final Color VERDE_TEXTO = new Color(23, 105, 90);

    private static final Font F_MARCA = fuente(FontFactory.HELVETICA_BOLD, 22, Color.WHITE);
    private static final Font F_MARCA_SUB = fuente(FontFactory.HELVETICA, 9.5f, new Color(233, 220, 242));
    private static final Font F_TITULO = fuente(FontFactory.HELVETICA_BOLD, 17, CIRUELA);
    private static final Font F_SECCION = fuente(FontFactory.HELVETICA_BOLD, 11.5f, BUGANVILIA);
    private static final Font F_ETIQUETA = fuente(FontFactory.HELVETICA, 9.5f, GRIS);
    private static final Font F_VALOR = fuente(FontFactory.HELVETICA_BOLD, 10.5f, TEXTO);
    private static final Font F_TEXTO = fuente(FontFactory.HELVETICA, 10, TEXTO);
    private static final Font F_PEQUENA = fuente(FontFactory.HELVETICA, 8.5f, GRIS);
    private static final Font F_MONTO = fuente(FontFactory.HELVETICA_BOLD, 21, CIRUELA);
    private static final Font F_ENCABEZADO_TABLA = fuente(FontFactory.HELVETICA_BOLD, 9.5f, Color.WHITE);
    private static final Font F_PIE = fuente(FontFactory.HELVETICA, 8, GRIS);

    private final ResourceLoader resourceLoader;
    private final ZoneId zona;

    public PdfGeneratorService(ResourceLoader resourceLoader,
                               @Value("${maxi-eventos.zona-horaria:America/Bogota}") String zonaHoraria) {
        this.resourceLoader = resourceLoader;
        this.zona = ZoneId.of(zonaHoraria);
    }

    // ------------------------------------------------------------------ contrato

    public byte[] generarContrato(Contrato contrato) {
        String contratoId = contrato.getId() == null ? SIN_DATO : contrato.getId().toString();
        String clausulas = plantilla("classpath:templates/contrato-clausulas.txt").strip();
        String estado = contrato.getEstado().name();
        String invitados = contrato.getCantidadAdultos() == null || contrato.getCantidadNinos() == null ? SIN_DATO
                : (contrato.getCantidadAdultos() + contrato.getCantidadNinos()) + " (" + contrato.getCantidadAdultos()
                + " adultos y " + contrato.getCantidadNinos() + " niños)";
        String tipo = contrato.getTipoEvento() == null ? SIN_DATO
                : TIPOS.getOrDefault(contrato.getTipoEvento(), contrato.getTipoEvento());

        return crear("Maxi Eventos · Contrato " + contratoId, "Contrato " + contratoId, documento -> {
            documento.add(cabecera());
            documento.add(titulo("CONTRATO DE SERVICIOS PARA EVENTO", "Contrato N.º " + contratoId));
            documento.add(franjaEstado(estado, "Creado el " + fechaHora(contrato.getCreadoEn())));

            documento.add(seccion("Datos del cliente"));
            documento.add(datos(new String[][] {
                    {"Nombre", texto(contrato.getClienteNombre())},
                    {"Teléfono", texto(contrato.getClienteTelefono())},
                    {"Correo", texto(contrato.getClienteCorreo())}}));

            documento.add(seccion("Datos del evento"));
            documento.add(datos(new String[][] {
                    {"Evento", contrato.getNombreEvento()},
                    {"Tipo de evento", tipo},
                    {"Fecha del evento", fecha(contrato.getFechaEvento())},
                    {"Invitados", invitados},
                    {"Lugar", lugar(contrato)},
                    {"Identificador del evento", contrato.getEventoId().toString()}}));

            documento.add(seccion("Valor"));
            documento.add(cajaValor("Valor total del contrato", dinero(contrato.getValorTotal(), contrato.getMoneda())));

            documento.add(seccion("Condiciones"));
            documento.add(parrafoJustificado(clausulas));

            documento.add(firmas(texto(contrato.getClienteNombre())));
        });
    }

    // ------------------------------------------------------------------ anexo

    public byte[] generarAnexo(Contrato contrato, List<ContratoAdicional> adicionales) {
        BigDecimal totalAdicionales = adicionales.stream().map(ContratoAdicional::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String moneda = contrato.getMoneda() != null ? contrato.getMoneda()
                : adicionales.stream().map(ContratoAdicional::getMoneda).filter(java.util.Objects::nonNull).findFirst().orElse(null);
        String contratoId = contrato.getId() == null ? SIN_DATO : contrato.getId().toString();
        String valorTotal = dinero(contrato.getValorTotal(), moneda);
        String valorConAdicionales = contrato.getValorTotal() == null ? SIN_DATO
                : dinero(contrato.getValorTotal().add(totalAdicionales), moneda);
        String nota = plantilla("classpath:templates/anexo-nota.txt").strip()
                .replace("{{valorTotal}}", valorTotal).replace("{{valorConAdicionales}}", valorConAdicionales);

        return crear("Maxi Eventos · Anexo del contrato " + contratoId, "Anexo del contrato " + contratoId, documento -> {
            documento.add(cabecera());
            documento.add(titulo("ANEXO DE SERVICIOS ADICIONALES", "Contrato N.º " + contratoId));
            documento.add(franjaEstado(null, "Generado el " + fechaHora(Instant.now())));

            documento.add(seccion("Evento"));
            documento.add(datos(new String[][] {
                    {"Evento", contrato.getNombreEvento()},
                    {"Cliente", texto(contrato.getClienteNombre())},
                    {"Fecha del evento", fecha(contrato.getFechaEvento())},
                    {"Identificador del evento", contrato.getEventoId().toString()}}));

            documento.add(seccion("Servicios adicionales"));
            documento.add(tablaAdicionales(adicionales, moneda));

            PdfPTable total = cajaValor("Total de servicios adicionales", dinero(totalAdicionales, moneda));
            total.setSpacingBefore(12);
            documento.add(total);

            documento.add(seccion("Condiciones"));
            documento.add(parrafoJustificado(nota));

            documento.add(firmas(texto(contrato.getClienteNombre())));
        });
    }

    // ------------------------------------------------------------------ piezas del diseño

    private static PdfPTable cabecera() throws DocumentException {
        PdfPTable tabla = new PdfPTable(1);
        tabla.setWidthPercentage(100);
        PdfPCell marca = new PdfPCell();
        marca.setBackgroundColor(CIRUELA);
        marca.setBorder(Rectangle.NO_BORDER);
        marca.setPadding(18);
        marca.addElement(new Paragraph("Maxi Eventos", F_MARCA));
        marca.addElement(new Paragraph("Bodas y quince años · Neiva, Huila", F_MARCA_SUB));
        tabla.addCell(marca);
        PdfPCell barra = new PdfPCell(new Phrase(" ", F_PEQUENA));
        barra.setFixedHeight(5);
        barra.setBackgroundColor(BUGANVILIA);
        barra.setBorder(Rectangle.NO_BORDER);
        tabla.addCell(barra);
        return tabla;
    }

    private static Paragraph titulo(String titulo, String subtitulo) {
        Paragraph p = new Paragraph();
        p.setSpacingBefore(20);
        p.add(new Phrase(titulo + "\n", F_TITULO));
        p.add(new Phrase(subtitulo, F_PEQUENA));
        return p;
    }

    /** Fila con el estado del contrato (insignia de color) a la izquierda y una fecha a la derecha. */
    private static PdfPTable franjaEstado(String estado, String fecha) throws DocumentException {
        PdfPTable tabla = new PdfPTable(2);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[] {45f, 55f});
        tabla.setSpacingBefore(10);

        PdfPCell izquierda;
        if (estado == null) {
            izquierda = new PdfPCell(new Phrase(" ", F_PEQUENA));
            izquierda.setBorder(Rectangle.NO_BORDER);
        } else {
            boolean firmado = "FIRMADO".equals(estado);
            Font fuente = fuente(FontFactory.HELVETICA_BOLD, 9.5f, firmado ? VERDE_TEXTO : AMBAR_TEXTO);
            izquierda = new PdfPCell(new Phrase("Estado: " + ESTADOS.getOrDefault(estado, estado), fuente));
            izquierda.setBackgroundColor(firmado ? VERDE_FONDO : AMBAR_FONDO);
            izquierda.setBorder(Rectangle.NO_BORDER);
            izquierda.setPadding(7);
            izquierda.setPaddingLeft(10);
        }
        tabla.addCell(izquierda);

        PdfPCell derecha = new PdfPCell(new Phrase(fecha, F_PEQUENA));
        derecha.setBorder(Rectangle.NO_BORDER);
        derecha.setHorizontalAlignment(Element.ALIGN_RIGHT);
        derecha.setVerticalAlignment(Element.ALIGN_MIDDLE);
        tabla.addCell(derecha);
        return tabla;
    }

    /** Título de sección: texto en buganvilia con una línea debajo. */
    private static PdfPTable seccion(String texto) {
        PdfPTable tabla = new PdfPTable(1);
        tabla.setWidthPercentage(100);
        tabla.setSpacingBefore(16);
        PdfPCell celda = new PdfPCell(new Phrase(texto, F_SECCION));
        celda.setBorder(Rectangle.BOTTOM);
        celda.setBorderColorBottom(BUGANVILIA);
        celda.setBorderWidthBottom(1.2f);
        celda.setPaddingLeft(0);
        celda.setPaddingBottom(4);
        tabla.addCell(celda);
        return tabla;
    }

    /** Pares etiqueta / valor en dos columnas, con una línea suave entre filas. */
    private static PdfPTable datos(String[][] filas) throws DocumentException {
        PdfPTable tabla = new PdfPTable(2);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[] {30f, 70f});
        for (String[] fila : filas) {
            tabla.addCell(celdaDato(fila[0], F_ETIQUETA));
            tabla.addCell(celdaDato(fila[1], F_VALOR));
        }
        return tabla;
    }

    private static PdfPCell celdaDato(String texto, Font fuente) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, fuente));
        celda.setBorder(Rectangle.BOTTOM);
        celda.setBorderColorBottom(LINEA);
        celda.setBorderWidthBottom(0.6f);
        celda.setPaddingTop(6);
        celda.setPaddingBottom(6);
        celda.setPaddingLeft(2);
        return celda;
    }

    /** Recuadro lila con una etiqueta pequeña y el importe en grande. */
    private static PdfPTable cajaValor(String etiqueta, String valor) {
        PdfPTable tabla = new PdfPTable(1);
        tabla.setWidthPercentage(100);
        tabla.setSpacingBefore(8);
        PdfPCell celda = new PdfPCell();
        celda.setBackgroundColor(LILA);
        celda.setBorder(Rectangle.NO_BORDER);
        celda.setPadding(14);
        celda.addElement(new Paragraph(etiqueta, F_ETIQUETA));
        celda.addElement(new Paragraph(valor, F_MONTO));
        tabla.addCell(celda);
        return tabla;
    }

    private static Paragraph parrafoJustificado(String texto) {
        Paragraph p = new Paragraph(texto, F_TEXTO);
        p.setAlignment(Element.ALIGN_JUSTIFIED);
        p.setLeading(14);
        p.setSpacingBefore(8);
        return p;
    }

    private static PdfPTable tablaAdicionales(List<ContratoAdicional> adicionales, String moneda) throws DocumentException {
        PdfPTable tabla = new PdfPTable(4);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[] {40f, 12f, 24f, 24f});
        tabla.setSpacingBefore(8);
        tabla.setHeaderRows(1);
        String[] columnas = {"Servicio", "Cantidad", "Valor unitario", "Subtotal"};
        for (int i = 0; i < columnas.length; i++) {
            PdfPCell cabecera = new PdfPCell(new Phrase(columnas[i], F_ENCABEZADO_TABLA));
            cabecera.setBackgroundColor(CIRUELA);
            cabecera.setBorder(Rectangle.NO_BORDER);
            cabecera.setPadding(7);
            cabecera.setHorizontalAlignment(i == 0 ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
            tabla.addCell(cabecera);
        }
        for (int i = 0; i < adicionales.size(); i++) {
            ContratoAdicional a = adicionales.get(i);
            Color fondo = i % 2 == 0 ? Color.WHITE : LILA;
            tabla.addCell(celdaFila(a.getNombre(), fondo, Element.ALIGN_LEFT, F_VALOR));
            tabla.addCell(celdaFila(String.valueOf(a.getCantidad()), fondo, Element.ALIGN_RIGHT, F_TEXTO));
            tabla.addCell(celdaFila(dinero(a.getValorUnitario(), moneda), fondo, Element.ALIGN_RIGHT, F_TEXTO));
            tabla.addCell(celdaFila(dinero(a.getSubtotal(), moneda), fondo, Element.ALIGN_RIGHT, F_VALOR));
        }
        return tabla;
    }

    private static PdfPCell celdaFila(String texto, Color fondo, int alineacion, Font fuente) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, fuente));
        celda.setBackgroundColor(fondo);
        celda.setBorder(Rectangle.BOTTOM);
        celda.setBorderColorBottom(LINEA);
        celda.setBorderWidthBottom(0.6f);
        celda.setPadding(7);
        celda.setHorizontalAlignment(alineacion);
        return celda;
    }

    /** Dos líneas de firma: la del cliente y la de Maxi Eventos. */
    private static PdfPTable firmas(String cliente) throws DocumentException {
        PdfPTable tabla = new PdfPTable(3);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[] {45f, 10f, 45f});
        tabla.setSpacingBefore(34);
        tabla.setKeepTogether(true);
        for (int i = 0; i < 3; i++) {
            PdfPCell espacio = new PdfPCell(new Phrase(" ", F_PEQUENA));
            espacio.setBorder(Rectangle.NO_BORDER);
            espacio.setFixedHeight(40);
            tabla.addCell(espacio);
        }
        tabla.addCell(celdaFirma("Firma del cliente", cliente));
        PdfPCell separador = new PdfPCell(new Phrase(" ", F_PEQUENA));
        separador.setBorder(Rectangle.NO_BORDER);
        tabla.addCell(separador);
        tabla.addCell(celdaFirma("Maxi Eventos", "Organizadora"));
        return tabla;
    }

    private static PdfPCell celdaFirma(String titulo, String nombre) {
        PdfPCell celda = new PdfPCell();
        celda.setBorder(Rectangle.TOP);
        celda.setBorderColorTop(GRIS);
        celda.setBorderWidthTop(0.8f);
        celda.setPaddingTop(5);
        celda.addElement(new Paragraph(titulo, F_VALOR));
        celda.addElement(new Paragraph(nombre, F_PEQUENA));
        return celda;
    }

    // ------------------------------------------------------------------ documento

    @FunctionalInterface
    private interface Armador {
        void armar(Document documento) throws DocumentException;
    }

    private static byte[] crear(String pie, String titulo, Armador armador) {
        try {
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            Document documento = new Document(PageSize.A4, 48, 48, 40, 60);
            PdfWriter escritor = PdfWriter.getInstance(documento, salida);
            escritor.setPageEvent(new Pie(pie));
            documento.addTitle(titulo);
            documento.addAuthor("Maxi Eventos");
            documento.open();
            armador.armar(documento);
            documento.close();
            return salida.toByteArray();
        } catch (DocumentException exception) {
            throw new IllegalStateException("No se pudo generar el PDF", exception);
        }
    }

    /** Pie de cada página: identificación del documento a la izquierda y número de página a la derecha. */
    private static final class Pie extends PdfPageEventHelper {
        private final String texto;

        Pie(String texto) {
            this.texto = texto;
        }

        @Override
        public void onEndPage(PdfWriter escritor, Document documento) {
            PdfContentByte lienzo = escritor.getDirectContent();
            float y = documento.bottom() - 22;
            ColumnText.showTextAligned(lienzo, Element.ALIGN_LEFT, new Phrase(texto, F_PIE), documento.left(), y, 0);
            ColumnText.showTextAligned(lienzo, Element.ALIGN_RIGHT,
                    new Phrase("Página " + escritor.getPageNumber(), F_PIE), documento.right(), y, 0);
        }
    }

    private static Font fuente(String familia, float tamano, Color color) {
        return FontFactory.getFont(familia, tamano, Font.NORMAL, color);
    }

    // ------------------------------------------------------------------ utilidades

    private String plantilla(String ruta) {
        Resource resource = resourceLoader.getResource(ruta);
        try (var input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo leer la plantilla " + ruta, exception);
        }
    }

    private static String lugar(Contrato contrato) {
        if (contrato.getLugarNombre() == null) {
            return contrato.getValorTotal() == null ? SIN_DATO : "Por definir";
        }
        return contrato.getLugarDireccion() == null ? contrato.getLugarNombre()
                : contrato.getLugarNombre() + " (" + contrato.getLugarDireccion() + ")";
    }

    private static String texto(Object valor) {
        return valor == null || valor.toString().isBlank() ? SIN_DATO : valor.toString();
    }

    private static String fecha(LocalDate fecha) {
        return fecha == null ? SIN_DATO : FECHA_LARGA.format(fecha);
    }

    private String fechaHora(Instant instante) {
        return instante == null ? SIN_DATO : FECHA_HORA.format(instante.atZone(zona));
    }

    /** 8700000 → "8.700.000,00 COP". */
    static String dinero(BigDecimal valor, String moneda) {
        if (valor == null) {
            return SIN_DATO;
        }
        DecimalFormat formato = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(ES_CO));
        return formato.format(valor) + (moneda == null ? "" : " " + moneda);
    }
}
