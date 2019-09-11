/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.pdfbox.multipdf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.COSObjectable;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;

/**
 * Utility class used to clone PDF objects. It keeps track of objects it has
 * already cloned.
 *
 */
public class PDFCloneUtility
{
    private final PDDocument destination;
    private final Map<Object,COSBase> clonedVersion = new HashMap<>();
    private final Set<COSBase> clonedValues = new HashSet<>();
    // It might be useful to use IdentityHashMap like in PDFBOX-4477 for speed,
    // but we need a really huge file to test this. A test with the file from PDFBOX-4477
    // did not show a noticeable speed difference.

    /**
     * Creates a new instance for the given target document.
     * @param dest the destination PDF document that will receive the clones
     */
    public PDFCloneUtility(PDDocument dest)
    {
        this.destination = dest;
    }

    /**
     * Returns the destination PDF document this cloner instance is set up for.
     * @return the destination PDF document
     */
    PDDocument getDestination()
    {
        return this.destination;
    }

    /**
     * Deep-clones the given object for inclusion into a different PDF document identified by
     * the destination parameter.
     * @param base the initial object as the root of the deep-clone operation
     * @return the cloned instance of the base object
     * @throws IOException if an I/O error occurs
     */
      public COSBase cloneForNewDocument( Object base ) throws IOException
      {
          if( base == null )
          {
              return null;
          }
          COSBase retval = clonedVersion.get(base);
          if( retval != null )
          {
              //we are done, it has already been converted.
              return retval;
          }
          if (base instanceof COSBase && clonedValues.contains(base))
          {
              // Don't clone a clone
              return (COSBase) base;
          }
          if (base instanceof List)
          {
              COSArray array = new COSArray();
              List<?> list = (List<?>) base;
              for (Object obj : list)
              {
                  array.add(cloneForNewDocument(obj));
              }
              retval = array;
          }
          else if( base instanceof COSObjectable && !(base instanceof COSBase) )
          {
              retval = cloneForNewDocument( ((COSObjectable)base).getCOSObject() );
          }
          else if( base instanceof COSObject )
          {
              COSObject object = (COSObject)base;
              retval = cloneForNewDocument( object.getObject() );
          }
          else if( base instanceof COSArray )
          {
              COSArray newArray = new COSArray();
              COSArray array = (COSArray)base;
              for( int i=0; i<array.size(); i++ )
              {
                  newArray.add( cloneForNewDocument( array.get( i ) ) );
              }
              retval = newArray;
          }
          else if( base instanceof COSStream )
          {
              COSStream originalStream = (COSStream)base;
              COSStream stream = destination.getDocument().createCOSStream();
              try (OutputStream output = stream.createRawOutputStream();
                   InputStream input = originalStream.createRawInputStream())
              {
                  IOUtils.copy(input, output);
              }
              clonedVersion.put( base, stream );
              for( Map.Entry<COSName, COSBase> entry :  originalStream.entrySet() )
              {
                  stream.setItem(entry.getKey(), cloneForNewDocument(entry.getValue()));
              }
              retval = stream;
          }
          else if( base instanceof COSDictionary )
          {
              COSDictionary dic = (COSDictionary)base;
              retval = new COSDictionary();
              clonedVersion.put( base, retval );
              for( Map.Entry<COSName, COSBase> entry : dic.entrySet() )
              {
                  ((COSDictionary)retval).setItem(
                          entry.getKey(),
                          cloneForNewDocument(entry.getValue()));
              }
          }
          else
          {
              retval = (COSBase)base;
          }
          clonedVersion.put( base, retval );
          clonedValues.add(retval);
          return retval;
      }

    public COSBase cloneForNewDocument( Object base, boolean compress ) throws IOException
    {
        if( base == null )
        {
            return null;
        }
        COSBase retval = clonedVersion.get(base);
        if( retval != null )
        {
            //we are done, it has already been converted.
            return retval;
        }
        if (base instanceof COSBase && clonedValues.contains(base))
        {
            // Don't clone a clone
            return (COSBase) base;
        }
        if (base instanceof List)
        {
            COSArray array = new COSArray();
            List<?> list = (List<?>) base;
            for (Object obj : list)
            {
                array.add(cloneForNewDocument(obj, compress));
            }
            retval = array;
        }
        else if( base instanceof COSObjectable && !(base instanceof COSBase) )
        {
            retval = cloneForNewDocument( ((COSObjectable)base).getCOSObject(), compress );
        }
        else if( base instanceof COSObject )
        {
            COSObject object = (COSObject)base;
            retval = cloneForNewDocument( object.getObject(), compress );
        }
        else if( base instanceof COSArray )
        {
            COSArray newArray = new COSArray();
            COSArray array = (COSArray)base;
            for( int i=0; i<array.size(); i++ )
            {
                newArray.add( cloneForNewDocument( array.get( i ), compress ) );
            }
            retval = newArray;
        }
        else if( base instanceof COSStream )
        {
            COSStream originalStream = (COSStream)base;
            COSStream stream = destination.getDocument().createCOSStream();

            try (OutputStream output = stream.createOutputStream(compress?COSName.FLATE_DECODE: null);
                 InputStream input = originalStream.createInputStream())
            {
                IOUtils.copy(input, output);
            }
            clonedVersion.put( base, stream );
            for( Map.Entry<COSName, COSBase> entry :  originalStream.entrySet() )
            {
                stream.setItem(entry.getKey(), cloneForNewDocument(entry.getValue(), compress));
            }
            if(!compress){
                stream.removeItem(COSName.FLATE_DECODE);
            }
            retval = stream;
        }
        else if( base instanceof COSDictionary )
        {
            COSDictionary dic = (COSDictionary)base;
            retval = new COSDictionary();
            clonedVersion.put( base, retval );
            for( Map.Entry<COSName, COSBase> entry : dic.entrySet() )
            {
                ((COSDictionary)retval).setItem(
                        entry.getKey(),
                        cloneForNewDocument(entry.getValue(), compress));
            }
        }
        else
        {
            retval = (COSBase)base;
        }
        clonedVersion.put( base, retval );
        clonedValues.add(retval);
        return retval;
    }

    public COSBase cloneForNewDocument( Object base, COSStream oldStream, List<Object> newTokens ) throws IOException
    {
        if( base == null )
        {
            return null;
        }
        COSBase retval = clonedVersion.get(base);
        if( retval != null )
        {
            //we are done, it has already been converted.
        }
        else if( base instanceof List)
        {
            COSArray array = new COSArray();
            List<?> list = (List<?>) base;
            for (Object obj : list)
            {
                array.add(cloneForNewDocument(obj, oldStream, newTokens));
            }
            retval = array;
        }
        else if( base instanceof COSObjectable && !(base instanceof COSBase) )
        {
            retval = cloneForNewDocument( ((COSObjectable)base).getCOSObject(), oldStream, newTokens );
            clonedVersion.put( base, retval );
        }
        else if( base instanceof COSObject )
        {
            COSObject object = (COSObject)base;
            retval = cloneForNewDocument( object.getObject(), oldStream, newTokens );
            clonedVersion.put( base, retval );
        }
        else if( base instanceof COSArray )
        {
            COSArray newArray = new COSArray();
            COSArray array = (COSArray)base;
            for( int i=0; i<array.size(); i++ )
            {
                newArray.add( cloneForNewDocument( array.get( i ), oldStream, newTokens ) );
            }
            retval = newArray;
            clonedVersion.put( base, retval );
        }
        else if( base instanceof COSStream )
        {
            COSStream originalStream = (COSStream)base;
            COSBase type = originalStream.getItem(COSName.TYPE);
            COSBase subType = originalStream.getItem(COSName.SUBTYPE);
            if(COSName.XOBJECT.equals(type) && COSName.FORM.equals(subType)){
                COSStream stream = destination.getDocument().createCOSStream();
                try (OutputStream output = stream.createRawOutputStream();
                     InputStream input = originalStream.createRawInputStream())
                {
                    IOUtils.copy(input, output);
                }
                clonedVersion.put( base, stream );
                for( Map.Entry<COSName, COSBase> entry :  originalStream.entrySet() )
                {
                    stream.setItem(entry.getKey(), cloneForNewDocument(entry.getValue(), oldStream, newTokens));
                }
                retval = stream;

                if(oldStream != null && base == oldStream){
                    OutputStream os = null;
                    PDStream newContents = new PDStream(stream);
                    try{
                        os = newContents.createOutputStream(COSName.FLATE_DECODE);
                        ContentStreamWriter writer = new ContentStreamWriter( os );
                        writer.writeTokens(newTokens);
                    }finally{
                        os.close();
                    }
                }
            }else{
                clonedVersion.put( base, originalStream );
                retval = originalStream;
            }

        }
        else if( base instanceof COSDictionary )
        {
            COSDictionary dic = (COSDictionary)base;
            retval = new COSDictionary();
            clonedVersion.put( base, retval );
            for( Map.Entry<COSName, COSBase> entry : dic.entrySet() )
            {
                ((COSDictionary)retval).setItem(
                        entry.getKey(),
                        cloneForNewDocument(entry.getValue(), oldStream, newTokens));
            }
        }
        else
        {
            retval = (COSBase)base;
        }
        clonedVersion.put( base, retval );
        return retval;
    }

      /**
       * Merges two objects of the same type by deep-cloning its members.
       * <br>
       * Base and target must be instances of the same class.
       * @param base the base object to be cloned
       * @param target the merge target
       * @throws IOException if an I/O error occurs
       */
      void cloneMerge( final COSObjectable base, COSObjectable target) throws IOException
      {
          if( base == null )
          {
              return;
          }
          COSBase retval = clonedVersion.get( base );
          if( retval != null )
          {
              return;
              //we are done, it has already been converted. // ### Is that correct for cloneMerge???
          }
          //TODO what when clone-merging a clone? Does it ever happen?
          if (!(base instanceof COSBase))
          {
              cloneMerge(base.getCOSObject(), target.getCOSObject());
          }
          else if( base instanceof COSObject )
          {
              if(target instanceof COSObject)
              {
                  cloneMerge(((COSObject) base).getObject(),((COSObject) target).getObject() );
              }
              else if (target instanceof COSDictionary || target instanceof COSArray)
              {
                  cloneMerge(((COSObject) base).getObject(), target);
              }
          }
          else if( base instanceof COSArray )
          {
              COSArray array = (COSArray)base;
              for( int i=0; i<array.size(); i++ )
              {
                  ((COSArray)target).add( cloneForNewDocument( array.get( i ) ) );
              }
          }
          else if( base instanceof COSStream )
          {
            // does that make sense???
              COSStream originalStream = (COSStream)base;
              COSStream stream = destination.getDocument().createCOSStream();
              try (OutputStream output = stream.createOutputStream(originalStream.getFilters()))
              {
                  IOUtils.copy(originalStream.createInputStream(), output);
              }
              clonedVersion.put( base, stream );
              for( Map.Entry<COSName, COSBase> entry : originalStream.entrySet() )
              {
                  stream.setItem(entry.getKey(), cloneForNewDocument(entry.getValue()));
              }
              retval = stream;
          }
          else if( base instanceof COSDictionary )
          {
              COSDictionary dic = (COSDictionary)base;
              clonedVersion.put( base, retval );
              for( Map.Entry<COSName, COSBase> entry : dic.entrySet() )
              {
                  COSName key = entry.getKey();
                  COSBase value = entry.getValue();
                  if (((COSDictionary)target).getItem(key) != null)
                  {
                      cloneMerge(value, ((COSDictionary)target).getItem(key));
                  }
                  else
                  {
                      ((COSDictionary)target).setItem( key, cloneForNewDocument(value));
                  }
              }
          }
          else
          {
              retval = (COSBase)base;
          }
          clonedVersion.put( base, retval );
          clonedValues.add(retval);
      }
}
