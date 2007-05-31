/*
 * Copyright 2002-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.sf.json.xml;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONFunction;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;
import net.sf.json.util.JSONUtils;
import nu.xom.Attribute;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;
import nu.xom.Node;
import nu.xom.Serializer;
import nu.xom.Text;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Utility class for transforming JSON to XML an back.<br>
 * When transforming JSONObject and JSONArray instances to XML, this class will
 * add hints for converting back to JSON.<br>
 * Examples:<br>
 *
 * <pre>
 * JSONObject json = JSONObject.fromObject("{\"name\":\"json\",\"bool\":true,\"int\":1}");
 * String xml = new XMLSerializer().write( json );
 * <xmp><o class="object">
 <name type="string">json</name>
 <bool type="boolean">true</bool>
 <int type="number">1</int>
 </o></xmp>
 * </pre><pre>
 * JSONArray json = JSONArray.fromObject("[1,2,3]");
 * String xml = new XMLSerializer().write( json );
 * <xmp><a class="array">
 <e type="number">1</e>
 <e type="number">2</e>
 <e type="number">3</e>
 </a></xmp>
 * </pre>
 *
 * @author Andres Almiray <aalmiray@users.sourceforge.net>
 */
public class XMLSerializer {
   private static final String[] EMPTY_ARRAY = new String[0];
   private static final Log log = LogFactory.getLog( XMLSerializer.class );

   /** the name for an JSONArray Element */
   private String arrayName;
   /** the name for an JSONArray's element Element */
   private String elementName;
   /** list of properties to be expanded from child to parent */
   private String[] expandableProperties;
   /** flag to be tolerant for incomplete namespace prefixes */
   private boolean namespaceLenient;
   /** Map of namespaces per element */
   private Map namespacesPerElement = new TreeMap();
   /** the name for an JSONObject Element */
   private String objectName;
   /** flag for trimming namespace prefix from element name */
   private boolean removeNamespacePrefixFromElements;
   /** the name for the root Element */
   private String rootName;
   /** Map of namespaces for root element */
   private Map rootNamespace = new TreeMap();
   /** flag for skipping namespaces while reading */
   private boolean skipNamespaces;
   /** flag for trimming spaces from string values */
   private boolean trimSpaces;
   /** flag for adding JSON types hints as attributes */
   private boolean typeHintsEnabled;

   /**
    * Creates a new XMLSerializer with default options.<br>
    * <ul>
    * <li><code>objectName</code>: 'o'</li>
    * <li><code>arrayName</code>: 'a'</li>
    * <li><code>elementName</code>: 'e'</li>
    * <li><code>typeHinstEnabled</code>: true</li>
    * <li><code>namespaceLenient</code>: false</li>
    * <li><code>expandableProperties</code>: []</li>
    * <li><code>skipNamespaces</code>: false</li>
    * <li><code>removeNameSpacePrefixFromElement</code>: false</li>
    * <li><code>trimSpaces</code>: false</li>
    * </ul>
    */
   public XMLSerializer() {
      setObjectName( "o" );
      setArrayName( "a" );
      setElementName( "e" );
      setTypeHintsEnabled( true );
      setNamespaceLenient( false );
      setSkipNamespaces( false );
      setRemoveNamespacePrefixFromElements( false );
      setTrimSpaces( false );
      setExpandableProperties( EMPTY_ARRAY );
   }

   /**
    * Adds a namespace declaration to the root element.
    *
    * @param prefix namespace prefix
    * @param uri namespace uri
    */
   public void addNamespace( String prefix, String uri ) {
      addNamespace( prefix, uri, null );
   }

   /**
    * Adds a namespace declaration to an element.<br>
    * If the elementName param is null or blank, the namespace declaration will
    * be added to the root element.
    *
    * @param prefix namespace prefix
    * @param uri namespace uri
    * @param elementName name of target element
    */
   public void addNamespace( String prefix, String uri, String elementName ) {
      if( StringUtils.isBlank( uri ) ){
         return;
      }
      if( prefix == null ){
         prefix = "";
      }
      if( StringUtils.isBlank( elementName ) ){
         rootNamespace.put( prefix.trim(), uri.trim() );
      }else{
         Map nameSpaces = (Map) namespacesPerElement.get( elementName );
         if( nameSpaces == null ){
            nameSpaces = new TreeMap();
            namespacesPerElement.put( elementName, nameSpaces );
         }
         nameSpaces.put( prefix, uri );
      }
   }

   /**
    * Removes all namespaces declarations (from root an elements).
    */
   public void clearNamespaces() {
      rootNamespace.clear();
      namespacesPerElement.clear();
   }

   /**
    * Removes all namespace declarations from an element.<br>
    * If the elementName param is null or blank, the declarations will be
    * removed from the root element.
    *
    * @param elementName name of target element
    */
   public void clearNamespaces( String elementName ) {
      if( StringUtils.isBlank( elementName ) ){
         rootNamespace.clear();
      }else{
         namespacesPerElement.remove( elementName );
      }
   }

   /**
    * Returns the name used for JSONArray.
    */
   public String getArrayName() {
      return arrayName;
   }

   /**
    * Returns the name used for JSONArray elements.
    */
   public String getElementName() {
      return elementName;
   }

   /**
    * Returns a list of properties to be expanded from child to parent.
    */
   public String[] getExpandableProperties() {
      return expandableProperties;
   }

   /**
    * Returns the name used for JSONArray.
    */
   public String getObjectName() {
      return objectName;
   }

   /**
    * Returns the name used for the root element.
    */
   public String getRootName() {
      return rootName;
   }

   /**
    * Returns wether this serializer is tolerant to namespaces without URIs or
    * not.
    */
   public boolean isNamespaceLenient() {
      return namespaceLenient;
   }

   /**
    * Returns wether this serializer will remove namespace prefix from elements
    * or not.
    */
   public boolean isRemoveNamespacePrefixFromElements() {
      return removeNamespacePrefixFromElements;
   }

   /**
    * Returns wether this serializer will skip adding namespace declarations to
    * elements or not.
    */
   public boolean isSkipNamespaces() {
      return skipNamespaces;
   }

   /**
    * Returns wether this serializer will trim leading and trealing whitespace
    * from values or not.
    */
   public boolean isTrimSpaces() {
      return trimSpaces;
   }

   /**
    * Returns true if JSON types will be included as attributes.
    */
   public boolean isTypeHintsEnabled() {
      return typeHintsEnabled;
   }

   /**
    * Creates a JSON value from a XML string.
    *
    * @param xml A well-formed xml document in a String
    * @return a JSONNull, JSONObject or JSONArray
    * @throws JSONException if the conversion from XML to JSON can't be made for
    *         I/O or format reasons.
    */
   public JSON read( String xml ) {
      JSON json = null;
      try{
         Document doc = new Builder().build( new StringReader( xml ) );
         Element root = doc.getRootElement();
         if( isNullObject( root ) ){
            return JSONNull.getInstance();
         }
         String defaultType = getType( root, JSONTypes.STRING );
         if( isArray( root, true ) ){
            json = processArrayElement( root, defaultType );
         }else{
            json = processObjectElement( root, defaultType );
         }
      }catch( JSONException jsone ){
         throw jsone;
      }catch( Exception e ){
         throw new JSONException( e );
      }
      return json;
   }

   /**
    * Removes a namespace from the root element.
    *
    * @param prefix namespace prefix
    */
   public void removeNamespace( String prefix ) {
      removeNamespace( prefix, null );
   }

   /**
    * Removes a namespace from the root element.<br>
    * If the elementName is null or blank, the namespace will be removed from
    * the root element.
    *
    * @param prefix namespace prefix
    * @param elementName name of target element
    */
   public void removeNamespace( String prefix, String elementName ) {
      if( prefix == null ){
         prefix = "";
      }
      if( StringUtils.isBlank( elementName ) ){
         rootNamespace.remove( prefix.trim() );
      }else{
         Map nameSpaces = (Map) namespacesPerElement.get( elementName );
         nameSpaces.remove( prefix );
      }
   }

   /**
    * Sets the name used for JSONArray.<br>
    * Default is 'a'.
    */
   public void setArrayName( String arrayName ) {
      this.arrayName = StringUtils.isBlank( arrayName ) ? "a" : arrayName;
   }

   /**
    * Sets the name used for JSONArray elements.<br>
    * Default is 'e'.
    */
   public void setElementName( String elementName ) {
      this.elementName = StringUtils.isBlank( elementName ) ? "e" : elementName;
   }

   /**
    * Sets the list of properties to be expanded from child to parent.
    */
   public void setExpandableProperties( String[] expandableProperties ) {
      this.expandableProperties = expandableProperties == null ? EMPTY_ARRAY : expandableProperties;
   }

   /**
    * Sets the namespace declaration to the root element.<br>
    * Any previous values are discarded.
    *
    * @param prefix namespace prefix
    * @param uri namespace uri
    */
   public void setNamespace( String prefix, String uri ) {
      setNamespace( prefix, uri, null );
   }

   /**
    * Adds a namespace declaration to an element.<br>
    * Any previous values are discarded. If the elementName param is null or
    * blank, the namespace declaration will be added to the root element.
    *
    * @param prefix namespace prefix
    * @param uri namespace uri
    * @param elementName name of target element
    */
   public void setNamespace( String prefix, String uri, String elementName ) {
      if( StringUtils.isBlank( uri ) ){
         return;
      }
      if( prefix == null ){
         prefix = "";
      }
      if( StringUtils.isBlank( elementName ) ){
         rootNamespace.clear();
         rootNamespace.put( prefix.trim(), uri.trim() );
      }else{
         Map nameSpaces = (Map) namespacesPerElement.get( elementName );
         if( nameSpaces == null ){
            nameSpaces = new TreeMap();
            namespacesPerElement.put( elementName, nameSpaces );
         }
         nameSpaces.clear();
         nameSpaces.put( prefix, uri );
      }
   }

   /**
    * Sets wether this serializer is tolerant to namespaces without URIs or not.
    */
   public void setNamespaceLenient( boolean namespaceLenient ) {
      this.namespaceLenient = namespaceLenient;
   }

   /**
    * Sets the name used for JSONObject.<br>
    * Default is 'o'.
    */
   public void setObjectName( String objectName ) {
      this.objectName = StringUtils.isBlank( objectName ) ? "o" : objectName;
   }

   /**
    * Sets if this serializer will remove namespace prefix from elements when
    * reading.
    */
   public void setRemoveNamespacePrefixFromElements( boolean removeNamespacePrefixFromElements ) {
      this.removeNamespacePrefixFromElements = removeNamespacePrefixFromElements;
   }

   /**
    * Sets the name used for the root element.
    */
   public void setRootName( String rootName ) {
      this.rootName = StringUtils.isBlank( rootName ) ? null : rootName;
   }

   /**
    * Sets if this serializer will skip adding namespace declarations to
    * elements when reading.
    */
   public void setSkipNamespaces( boolean skipNamespaces ) {
      this.skipNamespaces = skipNamespaces;
   }

   /**
    * Sets if this serializer will trim leading and trealing whitespace from
    * values when reading.
    */
   public void setTrimSpaces( boolean trimSpaces ) {
      this.trimSpaces = trimSpaces;
   }

   /**
    * Sets wether JSON types will be included as attributes.
    */
   public void setTypeHintsEnabled( boolean typeHintsEnabled ) {
      this.typeHintsEnabled = typeHintsEnabled;
   }

   /**
    * Writes a JSON value into a XML string with UTF-8 encoding.<br>
    *
    * @param json The JSON value to transform
    * @return a String representation of a well-formed xml document.
    * @throws JSONException if the conversion from JSON to XML can't be made for
    *         I/O reasons.
    */
   public String write( JSON json ) {
      return write( json, null );
   }

   /**
    * Writes a JSON value into a XML string with an specific encoding.<br>
    * If the encoding string is null it will use UTF-8.
    *
    * @param json The JSON value to transform
    * @param encoding The xml encoding to use
    * @return a String representation of a well-formed xml document.
    * @throws JSONException if the conversion from JSON to XML can't be made for
    *         I/O reasons or the encoding is not supported.
    */
   public String write( JSON json, String encoding ) {
      if( JSONNull.getInstance()
            .equals( json ) ){
         Element root = null;
         root = newElement( getRootName() == null ? getObjectName() : getRootName() );
         root.addAttribute( new Attribute( "null", "true" ) );
         Document doc = new Document( root );
         return writeDocument( doc, encoding );
      }else if( json instanceof JSONArray ){
         JSONArray jsonArray = (JSONArray) json;
         Element root = processJSONArray( jsonArray,
               newElement( getRootName() == null ? getArrayName() : getRootName() ),
               expandableProperties );
         Document doc = new Document( root );
         return writeDocument( doc, encoding );
      }else{
         JSONObject jsonObject = (JSONObject) json;
         Element root = null;
         if( jsonObject.isNullObject() ){
            root = newElement( getObjectName() );
            root.addAttribute( new Attribute( "null", "true" ) );
         }else{
            root = processJSONObject( jsonObject,
                  newElement( getRootName() == null ? getObjectName() : getRootName() ),
                  expandableProperties, true );
         }
         Document doc = new Document( root );
         return writeDocument( doc, encoding );
      }
   }

   private void addNameSpaceToElement( Element element ) {
      String elementName = null;
      if( element instanceof CustomElement ){
         elementName = ((CustomElement) element).getQName();
      }else{
         elementName = element.getQualifiedName();
      }
      Map nameSpaces = (Map) namespacesPerElement.get( elementName );
      if( nameSpaces != null && !nameSpaces.isEmpty() ){
         setNamespaceLenient( true );
         for( Iterator entries = nameSpaces.entrySet()
               .iterator(); entries.hasNext(); ){
            Map.Entry entry = (Map.Entry) entries.next();
            String prefix = (String) entry.getKey();
            String uri = (String) entry.getValue();
            if( StringUtils.isBlank( prefix ) ){
               element.setNamespaceURI( uri );
            }else{
               element.addNamespaceDeclaration( prefix, uri );
            }
         }
      }
   }

   private boolean checkChildElements( Element element, boolean isTopLevel ) {
      int childCount = element.getChildCount();
      Elements elements = element.getChildElements();
      int elementCount = elements.size();

      if( childCount == 1 && element.getChild( 0 ) instanceof Text ){
         return isTopLevel;
      }

      if( childCount == elementCount ){
         if( elementCount == 0 ){
            return true;
         }
         if( elementCount == 1 ){
            if( element.getChild( 0 ) instanceof Text ){
               return true;
            }else{
               return false;
            }
         }
      }

      if( childCount > elementCount ){
         for( int i = 0; i < childCount; i++ ){
            Node node = element.getChild( i );
            if( node instanceof Text ){
               Text text = (Text) node;
               if( StringUtils.isNotBlank( StringUtils.strip( text.getValue() ) ) ){
                  return false;
               }
            }
         }
      }

      String childName = elements.get( 0 )
            .getQualifiedName();
      for( int i = 1; i < elementCount; i++ ){
         if( childName.compareTo( elements.get( i )
               .getQualifiedName() ) != 0 ){
            return false;
         }
      }

      return true;
   }

   private String getClass( Element element ) {
      Attribute attribute = element.getAttribute( "class" );
      String clazz = null;
      if( attribute != null ){
         String clazzText = attribute.getValue()
               .trim();
         if( JSONTypes.OBJECT.compareToIgnoreCase( clazzText ) == 0 ){
            clazz = JSONTypes.OBJECT;
         }else if( JSONTypes.ARRAY.compareToIgnoreCase( clazzText ) == 0 ){
            clazz = JSONTypes.ARRAY;
         }
      }
      return clazz;
   }

   private String getType( Element element ) {
      return getType( element, null );
   }

   private String getType( Element element, String defaultType ) {
      Attribute attribute = element.getAttribute( "type" );
      String type = null;
      if( attribute != null ){
         String typeText = attribute.getValue()
               .trim();
         if( JSONTypes.BOOLEAN.compareToIgnoreCase( typeText ) == 0 ){
            type = JSONTypes.BOOLEAN;
         }else if( JSONTypes.NUMBER.compareToIgnoreCase( typeText ) == 0 ){
            type = JSONTypes.NUMBER;
         }else if( JSONTypes.INTEGER.compareToIgnoreCase( typeText ) == 0 ){
            type = JSONTypes.INTEGER;
         }else if( JSONTypes.FLOAT.compareToIgnoreCase( typeText ) == 0 ){
            type = JSONTypes.FLOAT;
         }else if( JSONTypes.OBJECT.compareToIgnoreCase( typeText ) == 0 ){
            type = JSONTypes.OBJECT;
         }else if( JSONTypes.ARRAY.compareToIgnoreCase( typeText ) == 0 ){
            type = JSONTypes.ARRAY;
         }else if( JSONTypes.STRING.compareToIgnoreCase( typeText ) == 0 ){
            type = JSONTypes.STRING;
         }else if( JSONTypes.FUNCTION.compareToIgnoreCase( typeText ) == 0 ){
            type = JSONTypes.FUNCTION;
         }
      }else{
         if( defaultType != null ){
            log.info( "Using default type " + defaultType );
            type = defaultType;
         }
      }
      return type;
   }

   private boolean isArray( Element element, boolean isTopLevel ) {
      boolean isArray = false;
      String clazz = getClass( element );
      if( clazz != null && clazz.equals( JSONTypes.ARRAY ) ){
         isArray = true;
      }else if( element.getAttributeCount() == 0 ){
         isArray = checkChildElements( element, isTopLevel );
      }else if( element.getAttributeCount() == 1
            && (element.getAttribute( "class" ) != null || element.getAttribute( "type" ) != null) ){
         isArray = checkChildElements( element, isTopLevel );
      }else if( element.getAttributeCount() == 2
            && (element.getAttribute( "class" ) != null && element.getAttribute( "type" ) != null) ){
         isArray = checkChildElements( element, isTopLevel );
      }

      if( isArray ){
         // check namespace
         for( int j = 0; j < element.getNamespaceDeclarationCount(); j++ ){
            String prefix = element.getNamespacePrefix( j );
            String uri = element.getNamespaceURI( prefix );
            if( !StringUtils.isBlank( uri ) ){
               return false;
            }
         }
      }

      return isArray;
   }

   private boolean isNullObject( Element element ) {
      if( element.getChildCount() == 0 ){
         if( element.getAttributeCount() == 0 ){
            return true;
         }else if( element.getAttribute( "null" ) != null ){
            return true;
         }else if( element.getAttributeCount() == 1
               && (element.getAttribute( "class" ) != null || element.getAttribute( "type" ) != null) ){
            return true;
         }else if( element.getAttributeCount() == 2
               && (element.getAttribute( "class" ) != null && element.getAttribute( "type" ) != null) ){
            return true;
         }
      }
      return false;
   }

   private boolean isObject( Element element, boolean isTopLevel ) {
      boolean isObject = false;
      if( !isArray( element, isTopLevel ) ){
         int childCount = element.getChildCount();

         if( childCount == 1 && element.getChild( 0 ) instanceof Text ){
            return isTopLevel;
         }

         isObject = true;
      }
      return isObject;
   }

   private Element newElement( String name ) {
      if( name.indexOf( ':' ) != -1 ){
         namespaceLenient = true;
      }
      return namespaceLenient ? new CustomElement( name ) : new Element( name );
   }

   private JSON processArrayElement( Element element, String defaultType ) {
      JSONArray jsonArray = new JSONArray();
      // process children (including text)
      int childCount = element.getChildCount();
      for( int i = 0; i < childCount; i++ ){
         Node child = element.getChild( i );
         if( child instanceof Text ){
            Text text = (Text) child;
            if( StringUtils.isNotBlank( StringUtils.strip( text.getValue() ) ) ){
               jsonArray.element( text.getValue() );
            }
         }else if( child instanceof Element ){
            setValue( jsonArray, (Element) child, defaultType );
         }
      }
      return jsonArray;
   }

   private Element processJSONArray( JSONArray array, Element root, String[] expandableProperties ) {
      int l = array.size();
      for( int i = 0; i < l; i++ ){
         Object value = array.get( i );
         Element element = processJSONValue( value, root, null, expandableProperties );
         root.appendChild( element );
      }
      return root;
   }

   private Element processJSONObject( JSONObject jsonObject, Element root,
         String[] expandableProperties, boolean isRoot ) {
      if( jsonObject.isNullObject() ){
         root.addAttribute( new Attribute( "null", "true" ) );
         return root;
      }else if( jsonObject.isEmpty() ){
         return root;
      }

      if( isRoot ){
         if( !rootNamespace.isEmpty() ){
            setNamespaceLenient( true );
            for( Iterator entries = rootNamespace.entrySet()
                  .iterator(); entries.hasNext(); ){
               Map.Entry entry = (Map.Entry) entries.next();
               String prefix = (String) entry.getKey();
               String uri = (String) entry.getValue();
               if( StringUtils.isBlank( prefix ) ){
                  root.setNamespaceURI( uri );
               }else{
                  root.addNamespaceDeclaration( prefix, uri );
               }
            }
         }
      }

      addNameSpaceToElement( root );

      Object[] names = jsonObject.names()
            .toArray();
      Arrays.sort( names );
      Element element = null;
      for( int i = 0; i < names.length; i++ ){
         String name = (String) names[i];
         Object value = jsonObject.get( name );
         if( name.startsWith( "@xmlns" ) ){
            setNamespaceLenient( true );
            int colon = name.indexOf( ':' );
            if( colon == -1 ){
               // do not override if already defined by nameSpaceMaps
               if( StringUtils.isBlank( root.getNamespaceURI() ) ){
                  root.setNamespaceURI( String.valueOf( value ) );
               }
            }else{
               String prefix = name.substring( colon + 1 );
               if( StringUtils.isBlank( root.getNamespaceURI( prefix ) ) ){
                  root.addNamespaceDeclaration( prefix, String.valueOf( value ) );
               }
            }
         }else if( name.startsWith( "@" ) ){
            root.addAttribute( new Attribute( name.substring( 1 ), String.valueOf( value ) ) );
         }else if( name.equals( "#text" ) ){
            if( value instanceof JSONArray ){
               root.appendChild( ((JSONArray) value).join( "", true ) );
            }else{
               root.appendChild( String.valueOf( value ) );
            }
         }else if( value instanceof JSONArray
               && (((JSONArray) value).isExpandElements() || ArrayUtils.contains(
                     expandableProperties, name )) ){
            JSONArray array = (JSONArray) value;
            int l = array.size();
            for( int j = 0; j < l; j++ ){
               Object item = array.get( j );
               element = newElement( name );
               if( item instanceof JSONObject ){
                  element = processJSONValue( (JSONObject) item, root, element,
                        expandableProperties );
               }else if( item instanceof JSONArray ){
                  element = processJSONValue( (JSONArray) item, root, element, expandableProperties );
               }else{
                  element = processJSONValue( item, root, element, expandableProperties );
               }
               addNameSpaceToElement( element );
               root.appendChild( element );
            }
         }else{
            element = newElement( name );
            element = processJSONValue( value, root, element, expandableProperties );
            addNameSpaceToElement( element );
            root.appendChild( element );
         }
      }
      return root;
   }

   private Element processJSONValue( Object value, Element root, Element target,
         String[] expandableProperties ) {
      if( target == null ){
         target = newElement( getElementName() );
      }
      if( JSONUtils.isBoolean( value ) ){
         if( isTypeHintsEnabled() ){
            target.addAttribute( new Attribute( "type", JSONTypes.BOOLEAN ) );
         }
         target.appendChild( value.toString() );
      }else if( JSONUtils.isNumber( value ) ){
         if( isTypeHintsEnabled() ){
            target.addAttribute( new Attribute( "type", JSONTypes.NUMBER ) );
         }
         target.appendChild( value.toString() );
      }else if( JSONUtils.isFunction( value ) ){
         if( value instanceof String ){
            value = JSONFunction.parse( (String) value );
         }
         JSONFunction func = (JSONFunction) value;
         if( isTypeHintsEnabled() ){
            target.addAttribute( new Attribute( "type", JSONTypes.FUNCTION ) );
         }
         String params = ArrayUtils.toString( func.getParams() );
         params = params.substring( 1 );
         params = params.substring( 0, params.length() - 1 );
         target.addAttribute( new Attribute( "params", params ) );
         target.appendChild( new Text( "<![CDATA[" + func.getText() + "]]>" ) );
      }else if( JSONUtils.isString( value ) ){
         if( isTypeHintsEnabled() ){
            target.addAttribute( new Attribute( "type", JSONTypes.STRING ) );
         }
         target.appendChild( value.toString() );
      }else if( value instanceof JSONArray ){
         if( isTypeHintsEnabled() ){
            target.addAttribute( new Attribute( "class", JSONTypes.ARRAY ) );
         }
         target = processJSONArray( (JSONArray) value, target, expandableProperties );
      }else if( value instanceof JSONObject ){
         if( isTypeHintsEnabled() ){
            target.addAttribute( new Attribute( "class", JSONTypes.OBJECT ) );
         }
         target = processJSONObject( (JSONObject) value, target, expandableProperties, false );
      }else if( JSONUtils.isNull( value ) ){
         if( isTypeHintsEnabled() ){
            target.addAttribute( new Attribute( "class", JSONTypes.OBJECT ) );
         }
         target.addAttribute( new Attribute( "null", "true" ) );
      }
      return target;
   }

   private JSON processObjectElement( Element element, String defaultType ) {
      if( isNullObject( element ) ){
         return JSONNull.getInstance();
      }
      JSONObject jsonObject = new JSONObject();

      if( !skipNamespaces ){
         for( int j = 0; j < element.getNamespaceDeclarationCount(); j++ ){
            String prefix = element.getNamespacePrefix( j );
            String uri = element.getNamespaceURI( prefix );
            if( StringUtils.isBlank( uri ) ){
               continue;
            }
            if( !StringUtils.isBlank( prefix ) ){
               prefix = ":" + prefix;
            }
            setOrAccumulate( jsonObject, "@xmlns" + prefix, trimSpaceFromValue( uri ) );
         }
      }

      // process attributes first
      int attrCount = element.getAttributeCount();
      for( int i = 0; i < attrCount; i++ ){
         Attribute attr = element.getAttribute( i );
         String attrname = attr.getQualifiedName();
         if( "class".compareToIgnoreCase( attrname ) == 0
               || "type".compareToIgnoreCase( attrname ) == 0 ){
            continue;
         }
         String attrvalue = attr.getValue();
         setOrAccumulate( jsonObject, "@" + removeNamespacePrefix( attrname ),
               trimSpaceFromValue( attrvalue ) );
      }

      // process children (including text)
      int childCount = element.getChildCount();
      for( int i = 0; i < childCount; i++ ){
         Node child = element.getChild( i );
         if( child instanceof Text ){
            Text text = (Text) child;
            if( StringUtils.isNotBlank( StringUtils.strip( text.getValue() ) ) ){
               setOrAccumulate( jsonObject, "#text", trimSpaceFromValue( text.getValue() ) );
            }
         }else if( child instanceof Element ){
            setValue( jsonObject, (Element) child, defaultType );
         }
      }

      return jsonObject;
   }

   private String removeNamespacePrefix( String name ) {
      if( isRemoveNamespacePrefixFromElements() ){
         int colon = name.indexOf( ':' );
         return colon != -1 ? name.substring( colon + 1 ) : name;
      }
      return name;
   }

   private void setOrAccumulate( JSONObject jsonObject, String key, Object value ) {
      if( jsonObject.has( key ) ){
         jsonObject.accumulate( key, value );
         Object val = jsonObject.get( key );
         if( val instanceof JSONArray ){
            ((JSONArray) val).setExpandElements( true );
         }
      }else{
         jsonObject.element( key, value );
      }
   }

   private void setValue( JSONArray jsonArray, Element element, String defaultType ) {
      String clazz = getClass( element );
      String type = getType( element );
      type = (type == null) ? defaultType : type;

      boolean classProcessed = false;
      if( clazz != null ){
         if( clazz.compareToIgnoreCase( JSONTypes.ARRAY ) == 0 ){
            jsonArray.element( processArrayElement( element, type ) );
            classProcessed = true;
         }else if( clazz.compareToIgnoreCase( JSONTypes.OBJECT ) == 0 ){
            jsonArray.element( processObjectElement( element, type ) );
            classProcessed = true;
         }
      }
      if( !classProcessed ){
         if( type.compareToIgnoreCase( JSONTypes.BOOLEAN ) == 0 ){
            jsonArray.element( Boolean.valueOf( element.getValue() ) );
         }else if( type.compareToIgnoreCase( JSONTypes.NUMBER ) == 0 ){
            // try integer first
            try{
               jsonArray.element( Integer.valueOf( element.getValue() ) );
            }catch( NumberFormatException e ){
               jsonArray.element( Double.valueOf( element.getValue() ) );
            }
         }else if( type.compareToIgnoreCase( JSONTypes.INTEGER ) == 0 ){
            jsonArray.element( Integer.valueOf( element.getValue() ) );
         }else if( type.compareToIgnoreCase( JSONTypes.FLOAT ) == 0 ){
            jsonArray.element( Double.valueOf( element.getValue() ) );
         }else if( type.compareToIgnoreCase( JSONTypes.FUNCTION ) == 0 ){
            String[] params = null;
            String text = element.getValue();
            Attribute paramsAttribute = element.getAttribute( "params" );
            if( paramsAttribute != null ){
               params = StringUtils.split( paramsAttribute.getValue(), "," );
            }
            jsonArray.element( new JSONFunction( params, text ) );
         }else if( type.compareToIgnoreCase( JSONTypes.STRING ) == 0 ){
            // see if by any chance has a 'params' attribute
            Attribute paramsAttribute = element.getAttribute( "params" );
            if( paramsAttribute != null ){
               String[] params = null;
               String text = element.getValue();
               params = StringUtils.split( paramsAttribute.getValue(), "," );
               jsonArray.element( new JSONFunction( params, text ) );
            }else{

               if( isArray( element, false ) ){
                  jsonArray.element( processArrayElement( element, defaultType ) );
               }else if( isObject( element, false ) ){
                  jsonArray.element( processObjectElement( element, defaultType ) );
               }else{
                  jsonArray.element( trimSpaceFromValue( element.getValue() ) );
               }
            }

         }
      }
   }

   private void setValue( JSONObject jsonObject, Element element, String defaultType ) {
      String clazz = getClass( element );
      String type = getType( element );
      type = (type == null) ? defaultType : type;

      boolean classProcessed = false;
      String key = removeNamespacePrefix( element.getQualifiedName() );
      if( clazz != null ){
         if( clazz.compareToIgnoreCase( JSONTypes.ARRAY ) == 0 ){
            setOrAccumulate( jsonObject, key, processArrayElement( element, type ) );
            classProcessed = true;
         }else if( clazz.compareToIgnoreCase( JSONTypes.OBJECT ) == 0 ){
            setOrAccumulate( jsonObject, key, processObjectElement( element, type ) );
            classProcessed = true;
         }
      }
      if( !classProcessed ){
         if( type.compareToIgnoreCase( JSONTypes.BOOLEAN ) == 0 ){
            setOrAccumulate( jsonObject, key, Boolean.valueOf( element.getValue() ) );
         }else if( type.compareToIgnoreCase( JSONTypes.NUMBER ) == 0 ){
            // try integer first
            try{
               setOrAccumulate( jsonObject, key, Integer.valueOf( element.getValue() ) );
            }catch( NumberFormatException e ){
               setOrAccumulate( jsonObject, key, Double.valueOf( element.getValue() ) );
            }
         }else if( type.compareToIgnoreCase( JSONTypes.INTEGER ) == 0 ){
            setOrAccumulate( jsonObject, key, Integer.valueOf( element.getValue() ) );
         }else if( type.compareToIgnoreCase( JSONTypes.FLOAT ) == 0 ){
            setOrAccumulate( jsonObject, key, Double.valueOf( element.getValue() ) );
         }else if( type.compareToIgnoreCase( JSONTypes.FUNCTION ) == 0 ){
            String[] params = null;
            String text = element.getValue();
            Attribute paramsAttribute = element.getAttribute( "params" );
            if( paramsAttribute != null ){
               params = StringUtils.split( paramsAttribute.getValue(), "," );
            }
            setOrAccumulate( jsonObject, key, new JSONFunction( params, text ) );
         }else if( type.compareToIgnoreCase( JSONTypes.STRING ) == 0 ){
            // see if by any chance has a 'params' attribute
            Attribute paramsAttribute = element.getAttribute( "params" );
            if( paramsAttribute != null ){
               String[] params = null;
               String text = element.getValue();
               params = StringUtils.split( paramsAttribute.getValue(), "," );
               setOrAccumulate( jsonObject, key, new JSONFunction( params, text ) );
            }else{

               if( isArray( element, false ) ){
                  setOrAccumulate( jsonObject, key, processArrayElement( element, defaultType ) );
               }else if( isObject( element, false ) ){
                  setOrAccumulate( jsonObject, key, processObjectElement( element, defaultType ) );
               }else{
                  setOrAccumulate( jsonObject, key, trimSpaceFromValue( element.getValue() ) );
               }
            }
         }
      }
   }

   private String trimSpaceFromValue( String value ) {
      if( isTrimSpaces() ){
         return value.trim();
      }
      return value;
   }

   private String writeDocument( Document doc, String encoding ) {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try{
         XomSerializer serializer = (encoding == null) ? new XomSerializer( baos )
               : new XomSerializer( baos, encoding );
         serializer.write( doc );
      }catch( IOException ioe ){
         throw new JSONException( ioe );
      }
      return baos.toString();
   }

   private static class CustomElement extends Element {
      private static String getName( String name ) {
         int colon = name.indexOf( ':' );
         if( colon != -1 ){
            return name.substring( colon + 1 );
         }
         return name;
      }

      private static String getPrefix( String name ) {
         int colon = name.indexOf( ':' );
         if( colon != -1 ){
            return name.substring( 0, colon );
         }
         return "";
      }

      private String prefix;

      public CustomElement( String name ) {
         super( CustomElement.getName( name ) );
         prefix = CustomElement.getPrefix( name );
      }

      public final String getQName() {
         if( prefix.length() == 0 ){
            return getLocalName();
         }else{
            return prefix + ":" + getLocalName();
         }
      }
   }

   private class XomSerializer extends Serializer {
      public XomSerializer( OutputStream out ) {
         super( out );
      }

      public XomSerializer( OutputStream out, String encoding ) throws UnsupportedEncodingException {
         super( out, encoding );
      }

      protected void write( Text text ) throws IOException {
         String value = text.getValue();
         if( value.startsWith( "<![CDATA[" ) && value.endsWith( "]]>" ) ){
            value = value.substring( 9 );
            value = value.substring( 0, value.length() - 3 );
            writeRaw( "<![CDATA[" );
            writeRaw( value );
            writeRaw( "]]>" );
         }else{
            super.write( text );
         }
      }

      protected void writeEmptyElementTag( Element element ) throws IOException {
         if( element instanceof CustomElement && isNamespaceLenient() ){
            writeTagBeginning( (CustomElement) element );
            writeRaw( "/>" );
         }else{
            super.writeEmptyElementTag( element );
         }
      }

      protected void writeEndTag( Element element ) throws IOException {
         if( element instanceof CustomElement && isNamespaceLenient() ){
            writeRaw( "</" );
            writeRaw( ((CustomElement) element).getQName() );
            writeRaw( ">" );
         }else{
            super.writeEndTag( element );
         }
      }

      protected void writeNamespaceDeclaration( String prefix, String uri ) throws IOException {
         if( !StringUtils.isBlank( uri ) ){
            super.writeNamespaceDeclaration( prefix, uri );
         }
      }

      protected void writeStartTag( Element element ) throws IOException {
         if( element instanceof CustomElement && isNamespaceLenient() ){
            writeTagBeginning( (CustomElement) element );
            writeRaw( ">" );
         }else{
            super.writeStartTag( element );
         }
      }

      private void writeTagBeginning( CustomElement element ) throws IOException {
         writeRaw( "<" );
         writeRaw( element.getQName() );
         writeAttributes( element );
         writeNamespaceDeclarations( element );
      }
   }
}