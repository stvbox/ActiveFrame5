//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.4-2 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2013.09.12 at 04:26:56 PM MSK 
//


package ru.intertrust.cm.performance.dataset.jaxb;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for objectType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="objectType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;choice maxOccurs="unbounded" minOccurs="0">
 *         &lt;element name="string" type="{}stringType" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element name="dateTime" type="{}dateTimeType" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element name="reference" type="{}referenceType" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element name="children" type="{}childrenType" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element name="long" type="{}longType" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element name="decimal" type="{}decimalType" maxOccurs="unbounded" minOccurs="0"/>
 *       &lt;/choice>
 *       &lt;attribute name="type" type="{http://www.w3.org/2001/XMLSchema}string" />
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "objectType", propOrder = {
    "stringOrDateTimeOrReference"
})
public class ObjectType {

    @XmlElements({
        @XmlElement(name = "string", type = StringType.class),
        @XmlElement(name = "dateTime", type = DateTimeType.class),
        @XmlElement(name = "reference", type = ReferenceType.class),
        @XmlElement(name = "children", type = ChildrenType.class),
        @XmlElement(name = "long", type = LongType.class),
        @XmlElement(name = "decimal", type = DecimalType.class)
    })
    protected List<FieldType> stringOrDateTimeOrReference;
    @XmlAttribute(name = "type")
    protected String type;

    /**
     * Gets the value of the stringOrDateTimeOrReference property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the stringOrDateTimeOrReference property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getStringOrDateTimeOrReference().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link StringType }
     * {@link DateTimeType }
     * {@link ReferenceType }
     * {@link ChildrenType }
     * {@link LongType }
     * {@link DecimalType }
     * 
     * 
     */
    public List<FieldType> getStringOrDateTimeOrReference() {
        if (stringOrDateTimeOrReference == null) {
            stringOrDateTimeOrReference = new ArrayList<FieldType>();
        }
        return this.stringOrDateTimeOrReference;
    }

    /**
     * Gets the value of the type property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the value of the type property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setType(String value) {
        this.type = value;
    }

}
