/*
 * Copyright (c) 2002-2016, Mairie de Paris
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice
 *     and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice
 *     and the following disclaimer in the documentation and/or other materials
 *     provided with the distribution.
 *
 *  3. Neither the name of 'Mairie de Paris' nor 'Lutece' nor the names of its
 *     contributors may be used to endorse or promote products derived from
 *     this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * License 1.0
 */
package fr.paris.lutece.plugins.identitystore.web.rs.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.ClientResponse.Status;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.multipart.FormDataMultiPart;

import fr.paris.lutece.plugins.identitystore.web.rs.dto.IdentityChangeDto;
import fr.paris.lutece.plugins.identitystore.web.rs.dto.IdentityDto;
import fr.paris.lutece.plugins.identitystore.web.rs.dto.ResponseDto;
import fr.paris.lutece.plugins.identitystore.web.service.IIdentityProvider;
import fr.paris.lutece.plugins.identitystore.web.service.IdentityNotFoundException;
import fr.paris.lutece.portal.service.util.AppException;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.portal.service.util.AppPropertiesService;

import net.sf.json.util.JSONUtils;

import java.io.IOException;

import javax.ws.rs.core.MediaType;

/**
 * IdentityRestClientService
 */
public final class IdentityRestClientService implements IIdentityProvider
{
	private ObjectMapper _mapper = new ObjectMapper();

	/**
	 * private constructor initialize mapper
	 */
	private IdentityRestClientService()
	{
		_mapper = new ObjectMapper();
		_mapper.enable( DeserializationFeature.UNWRAP_ROOT_VALUE );
		_mapper.enable( SerializationFeature.INDENT_OUTPUT );
		_mapper.enable( SerializationFeature.WRAP_ROOT_VALUE );
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IdentityDto getIdentity( String strIdConnection, String strCustomerId, String strClientCode )
			throws IdentityNotFoundException, AppException
	{
		AppLogService.debug( "Get identity attributes of " + strIdConnection );

		Client client = Client.create();
		WebResource webResource = client
				.resource(
						AppPropertiesService.getProperty( Constants.URL_IDENTITYSTORE_ENDPOINT )
								+ Constants.IDENTITY_PATH )
				.queryParam( Constants.PARAM_ID_CONNECTION, strIdConnection )
				.queryParam( Constants.PARAM_ID_CUSTOMER, strCustomerId )
				.queryParam( Constants.PARAM_CLIENT_CODE, strClientCode );

		ClientResponse response = webResource.accept( MediaType.APPLICATION_JSON ).get( ClientResponse.class );

		if( response.getStatus() != Status.OK.getStatusCode() )
		{
			if( response.getStatus() == Status.NOT_FOUND.getStatusCode() )
			{
				throw new IdentityNotFoundException( "no identity found for connectionId=" + strIdConnection + " customerId="
						+ strCustomerId + " clientAppCode=" + strClientCode );
			}
			else
			{
				throw new AppException( Constants.ERROR_MESSAGE + response.getStatus() );
			}
		}

		IdentityDto identityDto = null;
		String strJsonResponse = response.getEntity( String.class );

		if( JSONUtils.mayBeJSON( strJsonResponse ) )
		{
			try
			{
				identityDto = _mapper.readValue( strJsonResponse, IdentityDto.class );
			}
			catch( IOException e )
			{
				throw new AppException( Constants.ERROR_MESSAGE + e.getMessage() );
			}
		}
		else
		{
			throw new AppException( Constants.ERROR_MESSAGE + " not json response " + strJsonResponse );
		}

		return identityDto;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ResponseDto updateIdentity( IdentityChangeDto identityChange )
	{
		AppLogService.debug( "Update identity attributes" );

		Client client = Client.create();

		WebResource webResource = client.resource( AppPropertiesService
				.getProperty( Constants.URL_IDENTITYSTORE_ENDPOINT ) + Constants.IDENTITY_PATH );

		FormDataMultiPart formParams = new FormDataMultiPart();

		try
		{
			formParams.bodyPart( _mapper.writeValueAsString( identityChange ), MediaType.APPLICATION_JSON_TYPE );
		}
		catch( JsonProcessingException e )
		{
			try
			{
				formParams.close();
			}
			catch( IOException e1 )
			{
				throw new AppException( Constants.ERROR_MESSAGE + e.getMessage() );
			}

			throw new AppException( Constants.ERROR_MESSAGE + e.getMessage() );
		}

		ClientResponse response = webResource.type( MediaType.MULTIPART_FORM_DATA ).post( ClientResponse.class, formParams );

		if( response.getStatus() != Status.OK.getStatusCode() )
		{
			throw new AppException( Constants.ERROR_MESSAGE + response.getStatus() );
		}

		ResponseDto responseDto = null;

		if( response.hasEntity() && response.getType().toString().equals( MediaType.APPLICATION_JSON ) )
		{
			try
			{
				responseDto = _mapper.readValue( response.getEntity( String.class ), ResponseDto.class );
			}
			catch( IOException e )
			{
				throw new AppException( Constants.ERROR_MESSAGE + e.getMessage() );
			}
		}

		return responseDto;
	}
}
